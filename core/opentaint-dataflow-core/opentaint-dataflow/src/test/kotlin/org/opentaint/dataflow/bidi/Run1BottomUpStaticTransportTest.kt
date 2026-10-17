package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** The oracle executes scalar OrdinaryD. Production observations decode only at the test boundary. */
class Run1BottomUpStaticTransportTest {
    private val returned = AccessPathBase.Return

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(targets: List<MethodKey>, before: List<CleanStep> = emptyList(),
                     after: List<CleanStep> = emptyList()): CallPlan =
        CallPlan(setOf(STATIC, returned), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, before),
            CallStage.Callees(ADDED, RETURNED, targets),
            CallStage.Actions(RETURNED, REWRITTEN, after),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(STATIC), keepEdge(returned), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)

    private fun source(f: FiniteProgramFixtures, owner: MethodKey, paths: List<List<Int>>,
                       marks: List<TaintMark>) {
        val edges = paths.flatMap { path -> marks.map { mark ->
            PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(mark)), ExclusionSet.Empty)
        } }
        f.entry(owner, RuleStatement(summary(ZERO_KEEP, *edges.toTypedArray()), StatementSummary.EMPTY, emptyList()))
    }

    private fun check(f: FiniteProgramFixtures, root: MethodKey, fields: List<Int>, marks: List<TaintMark>,
                      policy: SchedulePolicy = SchedulePolicy.FIFO, sameUnit: Boolean = false,
                      seed: Long = 0, duplicate: Boolean = false): Pair<RunObservation, ProductionSchedule> {
        val config = f.config(roots = listOf(root))
        val scalar = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = policy, seed = seed,
            quantum = 2, duplicateDeliveries = duplicate)
        val actual = schedule.run()
        val paths = (scalar.processed + scalar.rawSummaries + scalar.publications + actual.processed +
            actual.rawSummaries + actual.publications).flatMap { row ->
            row.premise.map { it.fact } + row.conclusion.fact
        }
        val universe = BoundedDenotation((paths.map { it.base } + listOf(AccessPathBase.Zero, STATIC,
            returned, AccessPathBase.Argument(0))).distinct(),
            (fields + paths.flatMap { it.path } + f.field("outside finite support")).distinct(),
            (marks + f.mark("outside finite marks") + TaintMark.ZERO).distinct(), 2)
        val expected = scalar.semantic(universe)
        val observed = actual.semantic(universe)
        if (expected != observed) {
            val details = buildString {
                fun <K, V> maps(name: String, want: Map<K, V>, got: Map<K, V>) {
                    for (key in want.keys + got.keys) if (want[key] != got[key])
                        appendLine("$name key=$key expected=${want[key].toString().take(2000)} actual=${got[key].toString().take(2000)}")
                }
                fun <T> sets(name: String, want: Set<T>, got: Set<T>) {
                    if (want != got) appendLine("$name missing=${(want - got).toString().take(4000)} extra=${(got - want).toString().take(4000)}")
                }
                maps("processed", expected.processed, observed.processed)
                sets("initials", expected.initials, observed.initials)
                maps("rawSummaries", expected.rawSummaries, observed.rawSummaries)
                maps("publications", expected.publications, observed.publications)
                maps("selectedDemand", expected.selectedDemand, observed.selectedDemand)
                maps("links", expected.links, observed.links)
                sets("requests", expected.requests, observed.requests)
                sets("sourceHits", expected.sourceHits, observed.sourceHits)
                maps("witnesses", expected.witnesses, observed.witnesses)
            }
            throw AssertionError("Exact OrdinaryD mismatch policy=$policy sameUnit=$sameUnit seed=$seed\n$details\n" +
                "program=${f.describe()} traceTail=${schedule.trace.takeLast(60)}")
        }
        return actual to schedule
    }

    private fun fieldInitial(f: FiniteProgramFixtures, path: List<Int>): Pattern =
        f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR)

    @Test fun `transparent many-field chain retains diagonal origins with fewer physical relations`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Many"))
            val paths = (0 until 16).map { listOf(cls, f.field("f$it")) }
            val t = f.mark("T")
            val methods = (0 until 4).map { f.method("chain $it", listOf(listOf(1), listOf(2), emptyList()), 2) }
            val keys = methods.map { it.key() }
            source(f, keys.first(), paths, listOf(t))
            for (index in 0 until keys.lastIndex) f.call(keys[index], 1, call(listOf(keys[index + 1])))
            val (actual, schedule) = check(f, keys.first(), paths.flatten(), listOf(t))
            for (method in keys.drop(1)) assertEquals(paths.map { fieldInitial(f, it) }.toSet(),
                actual.initials.filter { it.method == method && it.initial.fact.mark == MarkSlot.STAR }
                    .map { it.initial }.toSet())
            val packedLinks = schedule.subscriptions.filter { it.caller in keys.drop(1) &&
                it.ref.premise is InitialAp && (it.ref.premise as InitialAp).isFlow && it.added.base == STATIC }
            assertEquals(32, packedLinks.size, "Two downstream calls carry all sixteen original fields")
            for (part in packedLinks) assertEquals((part.ref.premise as InitialAp).pathData.toList(),
                f.ops.leaves(part.added).single().fact.path, "Packing cannot create off-diagonal field relations")
            assertEquals(2, schedule.physicalStaticRelationCount)
            assertTrue(schedule.physicalStaticRelationCount < packedLinks.size)
            assertEquals(3, schedule.physicalOrdinarySubscriptionCount,
                "Concrete first-hop members share the ZERO caller ref; each call still has its ordinary ZERO relation")
            assertEquals(32, schedule.physicalStaticEntryMembershipCount)
        }
    }

    @Test fun `diamond and recursive call closure preserve full context and call bindings`() {
        for (recursive in listOf(false, true)) for (sameUnit in listOf(false, true))
            FiniteProgramFixtures().use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("Diamond"))
                val fp = listOf(cls, f.field("f")); val gp = listOf(cls, f.field("g")); val t = f.mark("T")
                val root = f.method("root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val relayMethod = f.method("relay", listOf(listOf(1), listOf(2), emptyList()), 2)
                val left = relayMethod.key("left"); val right = relayMethod.key("right")
                val leaf = f.method("leaf", if (recursive) listOf(listOf(1, 2), listOf(2), emptyList())
                    else listOf(listOf(1), listOf(2), emptyList()), 2).key()
                source(f, root, listOf(fp, gp), listOf(t))
                f.call(root, 1, call(listOf(left, right)))
                f.call(left, 1, call(listOf(leaf))); f.call(right, 1, call(listOf(leaf)))
                if (recursive) f.call(leaf, 1, call(listOf(left)))
                f.statement(leaf, 0, summary(keepEdge(STATIC), ZERO_KEEP,
                    starEdge(STATIC, fp, returned, emptyList())))
                val (actual, schedule) = check(f, root, fp + gp, listOf(t),
                    policy = SchedulePolicy.REPLAY_FIRST, sameUnit = sameUnit, duplicate = true)
                for (owner in listOf(left, right)) assertTrue(actual.rawSummaries.any { it.method == owner &&
                    it.premise == setOf(fieldInitial(f, fp)) && it.conclusion.fact.base == returned && it.conclusion.demand })
                assertEquals(setOf(left, right), schedule.subscriptions.filter { it.callee == leaf && it.added.base == STATIC }
                    .map { it.caller }.filter { it in setOf(left, right) }.toSet())
            }
    }

    @Test fun `definite prefix overwrite blocks killed entry while disjoint field reaches call`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Prefix"))
            val fp = listOf(cls, f.field("f")); val gp = listOf(cls, f.field("g")); val t = f.mark("T")
            val root = f.method("prefix root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val writer = f.method("prefix writer", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val leaf = f.method("prefix leaf", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(fp, gp), listOf(t))
            f.call(root, 1, call(listOf(writer)))
            f.statement(writer, 0, MicroEdgeBuilder().apply {
                write(STATIC, fp, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            f.call(writer, 1, call(listOf(leaf)))
            val (actual, _) = check(f, root, fp + gp, listOf(t))
            assertFalse(actual.initials.any { it.method == leaf && it.initial == fieldInitial(f, fp) })
            assertContains(actual.initials, ObservedInitial(leaf, fieldInitial(f, gp)))
            assertFalse(actual.links.any { it.caller == writer && it.callee == leaf &&
                it.added.fact.path == fp && it.added.fact.mark == MarkSlot.STAR })
        }
    }

    @Test fun `prestore self assignment keeps its original field through a later call`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Self"))
            val fp = listOf(cls, f.field("f")); val t = f.mark("T"); val local = AccessPathBase.LocalVar(0)
            val root = f.method("self root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val middle = f.method("self middle", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val leaf = f.method("self leaf", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(fp), listOf(t)); f.call(root, 1, call(listOf(middle)))
            f.statement(middle, 0, MicroEdgeBuilder().apply { read(local, STATIC, fp) }.build())
            f.statement(middle, 1, MicroEdgeBuilder().apply {
                write(STATIC, fp, weak = false, values = listOf(local), aliasPaths = emptyList())
            }.build())
            f.call(middle, 2, call(listOf(leaf)))
            val (actual, _) = check(f, root, fp, listOf(t))
            assertContains(actual.initials, ObservedInitial(leaf, fieldInitial(f, fp)))
            assertTrue(actual.links.any { it.caller == middle && it.callee == leaf &&
                it.premise == setOf(fieldInitial(f, fp)) && it.added.fact.path == fp })
        }
    }

    @Test fun `call suffix cleaner executes before identity can rejoin and routes original mark request`() {
        for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            FiniteProgramFixtures().use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("Suffix"))
                val fp = listOf(cls, f.field("f")); val t = f.mark("T")
                val root = f.method("suffix root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val middle = f.method("suffix middle", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val leaf = f.method("suffix leaf", listOf(listOf(1), emptyList()), 1).key()
                source(f, root, listOf(fp), listOf(t)); f.call(root, 1, call(listOf(middle)))
                f.call(middle, 1, call(listOf(leaf), after = listOf(CleanStep.Clean(
                    Cleaner(STATIC, f.ap.path(fp), CleanReach.AT_AND_BELOW, t)))))
                val (actual, _) = check(f, root, fp, listOf(t), policy)
                assertContains(actual.requests, ObservedRequest(middle, fieldInitial(f, fp), RequestKind.Mark(t)))
                assertTrue(actual.rawSummaries.any { it.method == middle && it.premise == setOf(fieldInitial(f, fp)) &&
                    (it.conclusion.fact.mark as? MarkSlot.Star)?.excluded?.contains(t) == true })
                assertTrue(actual.rawSummaries.none { it.method == middle &&
                    it.premise == setOf(fieldInitial(f, fp)) && it.conclusion.fact == fieldInitial(f, fp).fact },
                    "The changed mx payload is not an unqualified packed identity")
            }
    }

    @Test fun `changed field and excluded mark keep source origin rather than target origin`() {
        for (policy in listOf(SchedulePolicy.FIFO, SchedulePolicy.SEEDED)) FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Moved"))
            val fp = listOf(cls, f.field("f")); val gp = listOf(cls, f.field("g")); val t = f.mark("T")
            val root = f.method("move root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val middle = f.method("move middle", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val leaf = f.method("move leaf", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(fp), listOf(t)); f.call(root, 1, call(listOf(middle)))
            f.statement(middle, 0, summary(PathEdge(fieldInitial(f, fp).fact,
                PathFact(STATIC, gp, Tail.STAR, MarkSlot.Star(f.ap.markSetOf(t))), ExclusionSet.Empty), ZERO_KEEP))
            f.call(middle, 1, call(listOf(leaf)))
            f.statement(leaf, 0, summary(PathEdge(PathFact(STATIC, gp, Tail.EXACT, MarkSlot.Concrete(t)),
                PathFact(returned, emptyList(), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty), ZERO_KEEP))
            val (actual, schedule) = check(f, root, fp + gp, listOf(t), policy, seed = 31)
            assertContains(actual.requests, ObservedRequest(middle, fieldInitial(f, fp), RequestKind.Mark(t)))
            assertTrue(schedule.subscriptions.any { it.caller == middle && it.callee == leaf &&
                observedPremise(it.ref.premise) == setOf(fieldInitial(f, fp)) &&
                f.ops.leaves(it.added).any { leaf -> leaf.fact.path == gp } },
                "A transformed relation stays bound to its original f premise")
            assertFalse(actual.rawSummaries.any { it.method == middle && it.conclusion.fact.base == returned &&
                it.conclusion.fact.mark == MarkSlot.Concrete(t) }, "The excluded concrete mark cannot prove T")
        }
    }

    @Test fun `packed inputs cannot satisfy a whole ND condition without both concrete members`() {
        for (both in listOf(false, true)) for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            FiniteProgramFixtures().use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("Joined"))
                val fp = listOf(cls, f.field("f")); val gp = listOf(cls, f.field("g"))
                val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
                val root = f.method("join root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val relay = f.method("join relay", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val join = f.method("join callee", listOf(listOf(1), emptyList()), 1).key()
                val supplies = listOf(PathEdge(ZERO_FACT,
                    PathFact(STATIC, fp, Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty)) +
                    if (both) listOf(PathEdge(ZERO_FACT,
                        PathFact(STATIC, gp, Tail.EXACT, MarkSlot.Concrete(u)), ExclusionSet.Empty)) else emptyList()
                f.entry(root, RuleStatement(summary(ZERO_KEEP, *supplies.toTypedArray()), StatementSummary.EMPTY, emptyList()))
                f.call(root, 1, call(listOf(relay))); f.call(relay, 1, call(listOf(join)))
                val literals = listOf(f.pattern(STATIC, fp, mark = MarkSlot.Concrete(t)),
                    f.pattern(STATIC, gp, mark = MarkSlot.Concrete(u)))
                val target = PathFact(returned, emptyList(), Tail.EXACT, MarkSlot.Concrete(v))
                f.exit(join, 1, ExitRules(RuleStatement(StatementSummary(emptySet(), emptyList(),
                    listOf(ConjunctiveEdge(literals, target)), emptyMap()), StatementSummary.EMPTY, emptyList()), false, emptySet()))
                val (actual, _) = check(f, root, fp + gp, listOf(t, u, v), policy)
                val joined = actual.publications.filter { it.method == join && it.conclusion.fact == target }
                if (both) {
                    assertTrue(joined.isNotEmpty())
                    assertTrue(joined.all { it.premise == literals.toSet() }, "All ND members retain their full original marks and paths")
                    assertTrue(actual.rawSummaries.any { it.method == root && it.conclusion.fact == target })
                } else {
                    assertTrue(joined.isEmpty(), "A missing g/U input cannot be replaced with packed f FLOW")
                    assertTrue(actual.rawSummaries.none { it.method == root && it.conclusion.fact == target })
                }
            }
    }
}
