package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** Positional indexes are checked against complete AP matches, never against another index. */
class IndexCompletenessTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val modes = listOf(ApMode(true, Direction.FORWARD, 3),
        ApMode(false, Direction.FORWARD, 3), ApMode(false, Direction.BACKWARD, 3))
    private data class Match(val publication: Publication, val member: Int, val subscription: Subscription, val selected: Facts)
    private fun initial(f: StoreFixtures, path: List<Int> = emptyList(), tail: Tail = Tail.ANY,
                        mark: MarkSlot = MarkSlot.Concrete(f.mark("T")), e: ExclusionSet = ExclusionSet.Empty,
                        base: AccessPathBase = x) = f.manager.initial(base, f.manager.path(path), tail, e, mark)
    private fun sub(f: StoreFixtures, added: Facts, ordinal: Int = 0, zeroOnly: Boolean = false,
                    callerLayer: Layer = Layer.NORMAL,
                    premise: PremiseKey = if (added is FlowTree) f.ops.policy(added) else f.manager.zero): Subscription =
        Subscription(f.method, CallerRef(f.key("caller-$ordinal"), premise, callerLayer, f.inst(ordinal)),
            added.layer, added, zeroOnly)
    private fun scan(f: StoreFixtures, publications: List<Publication>, parts: List<Subscription>, mode: ApMode): Set<Match> =
        buildSet {
            for (pub in publications) for (member in 0 until pub.premise.size) for (part in parts) {
                val selected = if (part.zeroOnly) part.added.takeIf { pub.premise.isZero }
                    else f.ops.satisfying(part.added, pub.premise.member(member), mode)
                if (selected != null) add(Match(pub, member, part, selected))
            }
        }
    private fun replay(f: StoreFixtures, index: PublicationIndex, parts: List<Subscription>, mode: ApMode): Set<Match> =
        buildSet {
            for (part in parts) {
                val candidates = index.candidates(part, mode)
                assertEquals(candidates.size, candidates.distinct().size, "Each full premise/member is returned once")
                for ((pub, member) in candidates) matches(part, pub, member, f.ops, mode)?.let {
                    add(Match(pub, member, part, it))
                }
            }
        }
    private fun delivery(f: StoreFixtures, index: CalleeSubscriptions, publications: List<Publication>, mode: ApMode): Set<Match> =
        buildSet {
            for (pub in publications) for (member in 0 until pub.premise.size) {
                val candidates = index.candidates(pub, member, mode).toList()
                assertEquals(candidates.size, candidates.distinct().size, "Each accepted subscription part is returned once")
                for (part in candidates) matches(part, pub, member, f.ops, mode)?.let {
                    add(Match(pub, member, part, it))
                }
            }
        }
    private fun <P : TrieLeaf> walk(root: TrieNode<P>, action: (List<Int>, P) -> Unit) {
        val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
        pending.addLast(emptyList<Int>() to root)
        while (pending.isNotEmpty()) {
            val (path, node) = pending.removeLast()
            if (!node.leaf.isEmpty) action(path, node.leaf)
            val accessors = node.accessors
            val children = node.children
            if (accessors != null && children != null) for (i in accessors.indices)
                pending.addLast((path + accessors[i]) to children[i])
        }
    }
    private fun decoded(facts: Facts?): Set<Conclusion> = buildSet {
        if (facts == null) return@buildSet
        val demand = facts.layer == Layer.DEMAND
        fun put(path: List<Int>, tail: Tail, mark: MarkSlot, e: ExclusionSet = ExclusionSet.Empty) {
            add(Conclusion(PathFact(facts.base, path, tail, mark), e, demand))
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
    private class Prefix {
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun taint(f: StoreFixtures, leaves: List<Conclusion>, e: ExclusionSet = ExclusionSet.Empty,
                      layer: Layer = Layer.NORMAL): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            var at = root
            for (accessor in leaf.fact.path) at = at.children.getOrPut(accessor) { Prefix() }
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) at.exact += id else at.any += id
        }
        fun build(at: Prefix): TaintNode = f.manager.taintAlg.node(
            f.manager.taintLeaves(MarkSet(at.exact.toIntArray()), MarkSet(at.any.toIntArray())),
            at.children.keys.toIntArray(), at.children.values.map(::build).toTypedArray())
        return f.manager.taintTree(leaves.first().fact.base, layer, build(root), e)
    }
    private fun verifyBoth(f: StoreFixtures, pubs: List<Publication>, requests: List<Subscription>,
                           selectedModes: List<ApMode> = modes) {
        for (reverse in listOf(false, true)) {
            val orderedPubs = if (reverse) pubs.reversed() else pubs
            val orderedRequests = if (reverse) requests.reversed() else requests
            val published = PublicationIndex(f.manager, f.ops)
            assertEquals(orderedPubs, published.addAll(orderedPubs))
            val subscriptions = CalleeSubscriptions(f.ops)
            val parts = orderedRequests.mapNotNull(subscriptions::add)
            assertEquals(orderedRequests, parts)
            for (mode in selectedModes) {
                val expected = scan(f, pubs, parts, mode)
                val label = "reverse=$reverse, run1=${mode.run1}, direction=${mode.direction}"
                assertEquals(expected, replay(f, published, parts, mode), "replay: $label")
                assertEquals(expected, delivery(f, subscriptions, pubs, mode), "delivery: $label")
                assertTrue(expected.isNotEmpty(), label)
            }
        }
    }

    @Test
    fun `replay index retains every matching prefix extension and distinct member`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val t = MarkSlot.Concrete(f.mark("T"))
        val u = MarkSlot.Concrete(f.mark("U"))
        val premises = listOf(initial(f), initial(f, listOf(a)), initial(f, listOf(a, b)),
            initial(f, listOf(b)), initial(f, listOf(a), Tail.EXACT), initial(f, listOf(a), mark = u),
            initial(f, mark = MarkSlot.STAR, tail = Tail.STAR))
        val pubs = premises.mapIndexed { i, pr -> Publication(pr,
            f.fact(base = y, path = listOf(f.field("out-$i")), mark = if (pr.isFlow) MarkSlot.STAR else t,
                tail = if (pr.isFlow) Tail.STAR else Tail.EXACT)) }
        val index = PublicationIndex(f.manager, f.ops)
        assertEquals(pubs, index.addAll(pubs))
        val parts = listOf(f.fact(), f.fact(path = listOf(a)), f.fact(path = listOf(a, b)),
            f.fact(path = listOf(b)), f.fact(path = listOf(a), mark = u),
            f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)).mapIndexed { i, fact -> sub(f, fact, i) }
        for (mode in modes) assertEquals(scan(f, pubs, parts, mode), replay(f, index, parts, mode),
            "run1=${mode.run1}, direction=${mode.direction}")
    }

    @Test
    fun `delivery transpose retains every matching accepted part in either arrival order`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val pubs = listOf(initial(f), initial(f, listOf(a)), initial(f, listOf(a, b)), initial(f, listOf(b)),
            initial(f, tail = Tail.STAR, mark = MarkSlot.STAR)).map { pr -> Publication(pr,
            f.fact(base = y, tail = if (pr.isFlow) Tail.STAR else Tail.EXACT,
                mark = if (pr.isFlow) MarkSlot.STAR else MarkSlot.Concrete(f.mark("T")))) }
        val requests = listOf(f.fact(), f.fact(path = listOf(a)), f.fact(path = listOf(a, b)),
            f.fact(path = listOf(b)), f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)).mapIndexed { i, fact -> sub(f, fact, i) }
        for (order in listOf(requests, requests.reversed())) {
            val index = CalleeSubscriptions(f.ops)
            val accepted = order.mapNotNull(index::add)
            assertEquals(order, accepted)
            for (mode in modes) assertEquals(scan(f, pubs, accepted, mode), delivery(f, index, pubs, mode),
                "run1=${mode.run1}, direction=${mode.direction}")
        }
    }

    @Test
    fun `finite full scans retain tails exclusions marks layers bases and every multi-premise member`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val fresh = f.field("fresh")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val element = f.manager.accessors.index(ElementAccessor)
        val paths = listOf(emptyList(), listOf(a), listOf(a, b), listOf(b), listOf(cls, a), listOf(element), listOf(fresh))
        val e = listOf(ExclusionSet.Empty, ExclusionSet.of(a), ExclusionSet.of(b))
        val t = f.mark("T")
        val u = f.mark("U")
        val marks = listOf(t, u)
        val premises = buildList<PremiseKey> {
            add(f.manager.zero)
            for (path in paths) {
                for (mark in marks) {
                    add(initial(f, path, Tail.EXACT, MarkSlot.Concrete(mark)))
                    add(initial(f, path, Tail.ANY, MarkSlot.Concrete(mark)))
                    for (exclusion in e) add(initial(f, path, Tail.STAR, MarkSlot.Concrete(mark), exclusion))
                }
                for (exclusion in e) add(initial(f, path, Tail.STAR, MarkSlot.STAR, exclusion))
                add(initial(f, path, Tail.ANY, MarkSlot.STAR))
            }
            add(initial(f, listOf(a), base = y))
            add(f.manager.premiseOf(listOf(initial(f, listOf(a)), initial(f, listOf(a), mark = MarkSlot.Concrete(u)))))
            add(f.manager.premiseOf(listOf(initial(f, listOf(a)), initial(f, listOf(b)))))
        }.distinct()
        val pubs = premises.flatMapIndexed { i, pr -> Layer.entries.map { layer ->
            Publication(pr, f.fact(base = y, path = listOf(f.field("out-$i")), layer = layer,
                mark = if (pr.isFlow) MarkSlot.Star(f.manager.markSetOf(u)) else MarkSlot.Concrete(t),
                tail = if (pr.isFlow && layer == Layer.NORMAL) Tail.STAR else if (pr.isFlow) Tail.ANY else Tail.EXACT,
                exclusion = if (pr.isFlow && layer == Layer.NORMAL) ExclusionSet.of(b) else ExclusionSet.Empty))
        } }
        val inputs = buildList<Facts> {
            add(Reach.NORMAL)
            add(Reach.DEMAND)
            for (path in paths) for (layer in Layer.entries) {
                for (mark in marks) {
                    add(f.fact(path = path, mark = MarkSlot.Concrete(mark), layer = layer))
                    for (exclusion in if (layer == Layer.NORMAL) e else listOf(ExclusionSet.Empty))
                        add(f.fact(path = path, tail = if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
                            mark = MarkSlot.Concrete(mark), exclusion = exclusion, layer = layer))
                }
                for (mx in listOf(MarkSet.EMPTY, f.manager.markSetOf(t), f.manager.markSetOf(u)))
                    for (exclusion in if (layer == Layer.NORMAL) e else listOf(ExclusionSet.Empty))
                        add(f.fact(path = path, tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                            mark = MarkSlot.Star(mx), exclusion = exclusion, layer = layer))
            }
            add(f.fact(base = y, path = listOf(a)))
        }
        val parts = inputs.mapIndexed { i, fact -> sub(f, fact, i,
            callerLayer = if (i % 2 == 0) Layer.NORMAL else Layer.DEMAND) }
        verifyBoth(f, pubs, parts)
    }

    @Test
    fun `must and may siblings retain full premise keys and distinct E at identical indexed paths`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val u = f.mark("U")
        val e = listOf(ExclusionSet.Empty, ExclusionSet.of(a), ExclusionSet.of(b), ExclusionSet.of(intArrayOf(a, b)))
        val premises = buildList<PremiseKey> {
            for (path in listOf(emptyList(), listOf(a), listOf(a, b))) for (mark in listOf(t, u)) {
                add(initial(f, path, Tail.EXACT, MarkSlot.Concrete(mark)))
                add(initial(f, path, Tail.ANY, MarkSlot.Concrete(mark)))
                for (exclusion in e) add(initial(f, path, Tail.ANY_TAINT, MarkSlot.Concrete(mark), exclusion))
            }
            add(f.manager.premiseOf(listOf(initial(f, tail = Tail.ANY_TAINT, e = ExclusionSet.of(a)),
                initial(f, tail = Tail.ANY_TAINT, e = ExclusionSet.of(b)))))
            add(f.manager.premiseOf(listOf(initial(f, tail = Tail.ANY_TAINT, e = ExclusionSet.of(a)), initial(f))))
        }
        assertEquals(premises.size, premises.distinct().size)
        val pubs = premises.mapIndexed { i, pr -> Publication(pr,
            f.fact(base = y, path = listOf(f.field("must-out-$i")), layer = if (i % 2 == 0) Layer.NORMAL else Layer.DEMAND)) }
        val facts = buildList {
            for (path in listOf(emptyList(), listOf(a), listOf(a, b), listOf(b))) for (mark in listOf(t, u)) {
                add(f.fact(path = path, mark = MarkSlot.Concrete(mark)))
                add(f.fact(path = path, tail = Tail.ANY, mark = MarkSlot.Concrete(mark), layer = Layer.DEMAND))
                for (exclusion in e) add(f.fact(path = path, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(mark), exclusion = exclusion))
            }
        }
        verifyBoth(f, pubs, facts.mapIndexed { i, fact -> sub(f, fact, i) }, listOf(modes[1]))
    }

    @Test
    fun `restricted canonical FLOW overlap includes both directions but annotated E and concrete guards still apply`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val canonical = initial(f, listOf(a), Tail.STAR, MarkSlot.STAR)
        val may = initial(f, listOf(a), Tail.ANY, MarkSlot.STAR)
        val annotated = initial(f, tail = Tail.STAR, mark = MarkSlot.STAR, e = ExclusionSet.of(a))
        val concrete = initial(f, tail = Tail.STAR)
        val pubs = listOf(canonical, may, annotated, concrete).map { pr -> Publication(pr,
            f.fact(base = y, tail = if (pr.isFlow) Tail.STAR else Tail.EXACT,
                mark = if (pr.isFlow) MarkSlot.STAR else MarkSlot.Concrete(f.mark("T")))) }
        val above = sub(f, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR), 0)
        val below = sub(f, f.fact(path = listOf(a, b), tail = Tail.STAR, mark = MarkSlot.STAR), 1)
        val incomparable = sub(f, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR, exclusion = ExclusionSet.of(b)), 2)
        verifyBoth(f, pubs, listOf(above, below, incomparable), modes.drop(1))
        for (mode in modes.drop(1)) {
            assertEquals(above.added, matches(above, pubs[0], 0, f.ops, mode))
            assertEquals(below.added, matches(below, pubs[0], 0, f.ops, mode))
            assertEquals(above.added, matches(above, pubs[1], 0, f.ops, mode))
            assertNull(matches(incomparable, pubs[2], 0, f.ops, mode))
            assertNull(matches(above, pubs[3], 0, f.ops, mode))
        }
    }

    @Test
    fun `matches selects full marked leaves and preserves wrapper layer and E rather than narrowing locations`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val u = f.mark("U")
        val leaves = listOf(
            Conclusion(PathFact(x, emptyList(), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty, false),
            Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(u)), ExclusionSet.of(b), false),
            Conclusion(PathFact(x, listOf(a), Tail.EXACT, MarkSlot.Concrete(u)), ExclusionSet.Empty, false),
            Conclusion(PathFact(x, listOf(a), Tail.ANY_TAINT, MarkSlot.Concrete(t)), ExclusionSet.of(b), false),
            Conclusion(PathFact(x, listOf(a, b), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty, false),
            Conclusion(PathFact(x, listOf(b), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty, false))
        for (layer in Layer.entries) {
            val input = taint(f, leaves, ExclusionSet.of(b), layer)
            val part = sub(f, input)
            for (mode in modes) for (j in listOf(initial(f), initial(f, listOf(a)),
                initial(f, listOf(a), mark = MarkSlot.Concrete(u)), initial(f, listOf(a), Tail.EXACT))) {
                val pub = Publication(j, f.fact(base = y))
                val expected = decoded(input).filter { satisfies(j.toPattern(), Pattern(it.fact, it.exclusion), mode.restricted) }.toSet()
                val selected = matches(part, pub, 0, f.ops, mode)
                assertEquals(expected, decoded(selected))
                if (selected != null) {
                    assertEquals(layer, selected.layer)
                    assertEquals(x, selected.base)
                    if (selected is TaintTree && selected.root.hasAny) assertEquals(input.exclusion, selected.exclusion)
                }
            }
        }
    }

    @Test
    fun `publication deltas replay merged T1 content and whole changed FLOW E while old snapshots stay fixed`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val c = f.field("c")
        val index = PublicationIndex(f.manager, f.ops)
        val j = initial(f)
        val first = Publication(j, f.fact(base = y, path = listOf(a)))
        val second = Publication(j, f.fact(base = y, path = listOf(b)))
        val part = sub(f, f.fact())
        assertEquals(listOf(first), index.addAll(listOf(first)))
        val before = index.candidates(part, modes[0])
        assertEquals(listOf(first to 0), before)
        assertEquals(listOf(second), index.addAll(listOf(second)))
        assertTrue(index.addAll(listOf(first, second)).isEmpty())
        val expectedLeaves = decoded(first.conclusion) + decoded(second.conclusion)
        val merged = index.candidates(part, modes[0]).single()
        assertSame(j, merged.first.premise)
        assertEquals(0, merged.second)
        assertEquals(expectedLeaves, decoded(merged.first.conclusion))
        assertEquals(listOf(first to 0), before)
        val changedQuery = index.candidates(part, modes[0])
        runCatching { (changedQuery as MutableList<Pair<Publication, Int>>).clear() }
        assertEquals(expectedLeaves, decoded(index.candidates(part, modes[0]).single().first.conclusion))
        for (layer in Layer.entries) {
            val pr = initial(f, listOf(c), Tail.STAR, MarkSlot.STAR)
            val one = Publication(pr, f.fact(base = y, tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = MarkSlot.STAR, exclusion = if (layer == Layer.NORMAL) ExclusionSet.of(intArrayOf(a, b)) else ExclusionSet.Empty,
                layer = layer))
            val two = Publication(pr, f.fact(base = y, tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = MarkSlot.STAR, exclusion = if (layer == Layer.NORMAL) ExclusionSet.of(intArrayOf(b, c)) else ExclusionSet.Empty,
                layer = layer))
            assertEquals(listOf(one), index.addAll(listOf(one)))
            val expected = one.copy(conclusion = f.fact(base = y, tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = MarkSlot.STAR, exclusion = if (layer == Layer.NORMAL) ExclusionSet.of(b) else ExclusionSet.Empty,
                layer = layer))
            assertEquals(if (layer == Layer.NORMAL) listOf(expected) else emptyList(), index.addAll(listOf(two)))
            val input = sub(f, f.fact(path = listOf(c), tail = Tail.STAR, mark = MarkSlot.STAR), 1)
            val replayed = index.candidates(input, modes[0]).filter { it.first.premise === pr && it.first.layer == layer }
            assertEquals(listOf(expected to 0), replayed)
        }
    }

    @Test
    fun `subscription exact deltas retain denotationally contained deeper leaves and every complete caller reference`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val index = CalleeSubscriptions(f.ops)
        val root = sub(f, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR))
        val deeper = root.copy(added = f.fact(path = listOf(a), tail = Tail.STAR, mark = MarkSlot.STAR))
        assertEquals(root, index.add(root))
        assertNull(index.add(root))
        assertEquals(deeper, index.add(deeper), "Exact subscriptions do not use edge subsumption")
        assertNull(index.add(deeper))
        val variants = listOf(
            root.copy(ref = root.ref.copy(caller = f.key("another-context"))),
            root.copy(ref = root.ref.copy(call = f.inst(1))),
            root.copy(ref = root.ref.copy(premise = initial(f, listOf(a), Tail.STAR, MarkSlot.STAR))),
            root.copy(ref = root.ref.copy(callerLayer = Layer.DEMAND)),
            root.copy(linkLayer = Layer.DEMAND, added = f.fact(tail = Tail.ANY, mark = MarkSlot.STAR, layer = Layer.DEMAND)),
            root.copy(added = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR, exclusion = ExclusionSet.of(b))),
            root.copy(added = f.fact(tail = Tail.STAR, mark = MarkSlot.Star(f.manager.markSetOf(f.mark("U")))))
        )
        for (variant in variants) {
            assertEquals(variant, index.add(variant))
            assertNull(index.add(variant))
        }
        val pr = initial(f, listOf(a), Tail.STAR, MarkSlot.STAR)
        val pub = Publication(pr, f.fact(base = y, tail = Tail.STAR, mark = MarkSlot.STAR))
        val accepted = listOf(root, deeper) + variants
        for (mode in modes) assertEquals(scan(f, listOf(pub), accepted, mode), delivery(f, index, listOf(pub), mode))
        val run1 = delivery(f, index, listOf(pub), modes[0])
        assertTrue(run1.any { it.subscription == deeper })
        assertFalse(run1.any { it.subscription == root }, "The root FLOW subscription cannot replace the deeper run1 match")
        val before = index.candidates(pub, 0, modes[1]).toList()
        val late = root.copy(ref = root.ref.copy(caller = f.key("late")))
        assertEquals(late, index.add(late))
        assertEquals(accepted.toSet(), before.toSet())
        assertEquals((accepted + late).toSet(), index.candidates(pub, 0, modes[1]).toSet())
    }

    @Test
    fun `zero-only bypass applies only to the complete zero premise with separate caller layers and exact dedup`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val zero = Publication(f.manager.zero, f.fact(base = y))
        val concrete = Publication(initial(f), f.fact(base = y))
        val multi = Publication(f.manager.premiseOf(listOf(initial(f), initial(f, listOf(a)))), f.fact(base = y))
        val pubs = listOf(zero, concrete, multi)
        val parts = Layer.entries.map { layer -> sub(f, Reach.of(layer), zeroOnly = true, callerLayer = layer) }
        val ordinary = sub(f, Reach.NORMAL, 1)
        verifyBoth(f, pubs, parts + ordinary)
        val index = CalleeSubscriptions(f.ops)
        for (part in parts) {
            assertEquals(part, index.add(part))
            assertNull(index.add(part))
            for (mode in modes) {
                assertSame(part.added, matches(part, zero, 0, f.ops, mode))
                assertNull(matches(part, concrete, 0, f.ops, mode))
                for (m in 0 until multi.premise.size) assertNull(matches(part, multi, m, f.ops, mode))
            }
        }
        for (mode in modes) {
            assertEquals(parts.toSet(), index.candidates(zero, 0, mode).toSet())
            assertTrue(index.candidates(concrete, 0, mode).none())
            for (m in 0 until multi.premise.size) assertTrue(index.candidates(multi, m, mode).none())
        }
    }

    @Test
    fun `fanout above sixteen and repeated query leaves neither lose branches nor duplicate full members`(): Unit {
        val f = StoreFixtures()
        val branches = (0 until 33).map { f.field("branch-$it") }
        val t = MarkSlot.Concrete(f.mark("T"))
        val premises = branches.map { initial(f, listOf(it)) }
        val multi = f.manager.premiseOf(premises)
        val pubs = premises.mapIndexed { i, pr -> Publication(pr,
            f.fact(base = y, path = listOf(f.field("result-$i")))) } + Publication(multi, f.fact(base = y)) +
            Publication(initial(f, tail = Tail.STAR, mark = MarkSlot.STAR), f.fact(base = y, tail = Tail.STAR, mark = MarkSlot.STAR))
        val leaves = branches.map { Conclusion(PathFact(x, listOf(it), Tail.EXACT, t), ExclusionSet.Empty, false) }
        val wide = sub(f, taint(f, leaves), 0)
        val parts = listOf(wide, sub(f, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR), 1)) +
            branches.mapIndexed { i, branch -> sub(f, f.fact(path = listOf(branch)), i + 2) } +
            sub(f, f.fact(path = listOf(f.field("fresh-unindexed"))), 40)
        verifyBoth(f, pubs, parts)
        val index = PublicationIndex(f.manager, f.ops)
        index.addAll(pubs)
        for (mode in modes) assertEquals(branches.size,
            index.candidates(wide, mode).count { it.first.premise === multi }, "Each member keeps the complete ND key")
    }

    @Test
    fun `publication kind and temporary guards reject before index admission in both layers`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val concrete = initial(f)
        val flow = initial(f, tail = Tail.STAR, mark = MarkSlot.STAR)
        val multi = f.manager.premiseOf(listOf(concrete, initial(f, listOf(a))))
        for (layer in Layer.entries) for ((pr, bad) in listOf(
            concrete to f.fact(tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY, mark = MarkSlot.STAR, layer = layer),
            flow to f.fact(layer = layer), flow to Reach.of(layer), multi to Reach.of(layer),
            concrete to f.fact(base = AccessPathBase.CleanerTemporary(), layer = layer),
            flow to f.fact(base = AccessPathBase.CleanerTemporary(), tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = MarkSlot.STAR, layer = layer))) {
            val index = PublicationIndex(f.manager, f.ops)
            assertFailsWith<IllegalStateException> { index.addAll(listOf(Publication(pr, bad))) }
            val good = Publication(pr, if (pr.isFlow) f.fact(base = y, tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = MarkSlot.STAR, layer = layer) else f.fact(base = y, layer = layer))
            assertEquals(listOf(good), index.addAll(listOf(good)))
            assertTrue(index.addAll(listOf(good)).isEmpty())
            val parts = listOf(sub(f, f.fact()), sub(f, f.fact(path = listOf(a))),
                sub(f, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)))
            assertEquals(scan(f, listOf(good), parts, modes[0]), replay(f, index, parts, modes[0]))
        }
    }

    @Test
    fun `subscription guards reject zero-only shape layer and temporary errors before storing their reference`(): Unit {
        val f = StoreFixtures()
        val index = CalleeSubscriptions(f.ops)
        val good = sub(f, Reach.NORMAL, zeroOnly = true)
        val bad = listOf(good.copy(added = f.fact()),
            good.copy(ref = good.ref.copy(premise = initial(f))),
            good.copy(linkLayer = Layer.DEMAND))
        for (part in bad) assertFailsWith<IllegalStateException> { index.add(part) }
        assertEquals(good, index.add(good))
        assertNull(index.add(good))
        val zero = Publication(f.manager.zero, Reach.NORMAL)
        assertEquals(listOf(good), index.candidates(zero, 0, modes[0]).toList())
        for (layer in Layer.entries) for (flow in listOf(false, true)) {
            val valid = sub(f, f.fact(layer = layer, tail = if (!flow) Tail.EXACT else if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = if (flow) MarkSlot.STAR else MarkSlot.Concrete(f.mark("T"))), 1)
            val temporary = valid.copy(added = f.fact(base = AccessPathBase.CleanerTemporary(), layer = layer,
                tail = if (!flow) Tail.EXACT else if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
                mark = if (flow) MarkSlot.STAR else MarkSlot.Concrete(f.mark("T"))))
            assertFailsWith<IllegalStateException> { index.add(temporary) }
            assertEquals(valid, index.add(valid))
            assertNull(index.add(valid))
        }
        val mismatch = sub(f, f.fact(), 2).copy(linkLayer = Layer.DEMAND)
        assertFailsWith<IllegalStateException> { index.add(mismatch) }
        assertEquals(mismatch.copy(linkLayer = Layer.NORMAL), index.add(mismatch.copy(linkLayer = Layer.NORMAL)))
    }

    @Test
    fun `pre-cancelled index admission and queries leave no accepted state and activation keeps normal matches`(): Unit {
        val f = StoreFixtures()
        val pub = Publication(initial(f), f.fact(base = y))
        val part = sub(f, f.fact())
        val publications = PublicationIndex(f.manager, f.ops)
        val subscriptions = CalleeSubscriptions(f.ops)
        f.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { publications.addAll(listOf(pub)) }
        assertFailsWith<Cancellation.Cancelled> { subscriptions.add(part) }
        assertFailsWith<Cancellation.Cancelled> { publications.candidates(part, modes[0]) }
        assertFailsWith<Cancellation.Cancelled> { subscriptions.candidates(pub, 0, modes[0]).toList() }
        assertFailsWith<Cancellation.Cancelled> { matches(part, pub, 0, f.ops, modes[0]) }
        f.cancellation.activate()
        assertEquals(listOf(pub), publications.addAll(listOf(pub)))
        assertEquals(part, subscriptions.add(part))
        for (mode in modes) {
            val expected = scan(f, listOf(pub), listOf(part), mode)
            assertEquals(expected, replay(f, publications, listOf(part), mode))
            assertEquals(expected, delivery(f, subscriptions, listOf(pub), mode))
        }
    }

    @Test
    fun `lazy delivery candidates honor cancellation after creation and between retained parts for every kind`(): Unit {
        for (kind in FactsKind.entries) for (layer in Layer.entries) {
            val f = StoreFixtures()
            val pub = Publication(if (kind == FactsKind.REACH) f.manager.zero
                else initial(f, tail = if (kind == FactsKind.FLOW) Tail.STAR else Tail.ANY,
                    mark = if (kind == FactsKind.FLOW) MarkSlot.STAR else MarkSlot.Concrete(f.mark("T"))),
                if (kind == FactsKind.FLOW) f.fact(base = y, tail = Tail.STAR, mark = MarkSlot.STAR) else f.fact(base = y))
            val added = when (kind) {
                FactsKind.REACH -> Reach.of(layer)
                FactsKind.FLOW -> f.fact(tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY, mark = MarkSlot.STAR, layer = layer)
                FactsKind.TAINT -> f.fact(layer = layer)
            }
            val index = CalleeSubscriptions(f.ops)
            val parts = (0 until 3).map { sub(f, added, it, zeroOnly = kind == FactsKind.REACH) }
            for (part in parts) assertEquals(part, index.add(part))
            val created = index.candidates(pub, 0, modes[0])
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { created.iterator().next() }
            f.cancellation.activate()
            val started = index.candidates(pub, 0, modes[0]).iterator()
            assertEquals(parts[0], started.next())
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { started.next() }
            f.cancellation.activate()
            assertEquals(parts, index.candidates(pub, 0, modes[0]).toList())
        }
    }

    @Test
    fun `deep sparse publication and subscription indexes preserve complete paths without recursive traversal`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val path = List(32768) { a }
        val pub = Publication(initial(f, path, Tail.EXACT), f.fact(base = y))
        val part = sub(f, f.fact(path = path))
        val publications = PublicationIndex(f.manager, f.ops)
        val subscriptions = CalleeSubscriptions(f.ops)
        assertEquals(listOf(pub), publications.addAll(listOf(pub)))
        assertEquals(part, subscriptions.add(part))
        for (mode in modes) {
            assertEquals(listOf(pub to 0), publications.candidates(part, mode))
            assertEquals(listOf(part), subscriptions.candidates(pub, 0, mode).toList())
            assertEquals(part.added, matches(part, pub, 0, f.ops, mode))
        }
    }
}
