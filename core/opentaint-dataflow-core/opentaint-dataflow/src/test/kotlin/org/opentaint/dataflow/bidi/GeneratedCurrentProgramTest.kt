package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import java.util.Random
import kotlin.test.*

/** Generated finite AP programs. Multiple fields are a generic micro-edge test, not a JVM lowering claim. */
class GeneratedCurrentProgramTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val ret = AccessPathBase.Return
    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(callee: MethodKey, bases: Set<AccessPathBase> = setOf(x),
                     actions: List<CleanStep> = emptyList()): CallPlan = CallPlan(bases + ret, listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(*(bases.map(::keepEdge) + ZERO_KEEP).toTypedArray())),
        CallStage.Actions(BOUND, ADDED, emptyList()),
        CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, actions),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(ret), ZERO_KEEP)),
    ), emptyList(), BEFORE, AFTER)

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun compare(f: FiniteProgramFixtures, config: RunConfig, seed: Long,
                        fields: List<Int>, marks: List<TaintMark>, catalogRefinement: Boolean = false): RunObservation {
        check(!catalogRefinement || config.run1) { "Only run-1 static transport is replaced by the catalog" }
        val initialAdmission = if (catalogRefinement)
            NaiveClosure.InitialAdmission.RUN1_CONCRETE_CHAIN
        else NaiveClosure.InitialAdmission.HISTORICAL
        val expected = NaiveClosure(f, config, initialAdmission, semantics = NaiveClosure.Semantics.SELECTED).run()
        if (catalogRefinement) assertTrue(expected.initials.none {
            it.initial.fact.tail == Tail.ANY || it.initial.fact.tail == Tail.ANY_TAINT
        }, "The independent selected run-1 closure must construct STAR, never ANY, initials")
        fun facts(o: RunObservation): List<PathFact> = buildList {
            for (e in o.processed + o.rawSummaries + o.publications + o.selectedDemand) {
                addAll(e.premise.map { it.fact }); add(e.conclusion.fact)
            }
            addAll(o.initials.map { it.initial.fact })
            for (l in o.links) { addAll(l.premise.map { it.fact }); add(l.added.fact) }
            addAll(o.requests.map { it.premise.fact })
            for (w in o.witnesses) for (e in w.edges) {
                addAll(e.premise.map { it.fact }); addAll(e.facts.map { it.fact })
            }
        }
        var first: RunObservation? = null
        for (sameUnit in listOf(false, true)) {
            val schedule = ProductionSchedule(f, config, sameUnit, SchedulePolicy.SEEDED, seed,
                quantum = 1 + (seed % 3).toInt(), duplicateDeliveries = seed % 2 == 0L)
            val actual = schedule.run()
            val all = facts(expected) + facts(actual)
            val universe = BoundedDenotation(
                (all.map { it.base } + listOf(AccessPathBase.Zero, x, y, ret, AccessPathBase.ClassStatic)).distinct(),
                (fields + all.flatMap { it.path } + f.field("fresh accessor")).distinct(),
                (marks + all.mapNotNull { (it.mark as? MarkSlot.Concrete)?.mark } + f.mark("fresh mark") + TaintMark.ZERO).distinct(),
                2)
            try {
                if (catalogRefinement) expected.assertCatalogRefinement(actual, universe, config.roots, initialAdmission)
                else actual.assertEquivalent(expected, universe)
            }
            catch (failure: AssertionError) {
                throw AssertionError("generated seed=$seed run=${config.index} sameUnit=$sameUnit " +
                    "program=${f.describe()} inputs=${f.inputs(config)} trace=${schedule.trace}", failure)
            }
            first = first ?: actual
        }
        return checkNotNull(first)
    }

    @Test
    fun `generated read and cleaner geometries reach the direct closure in forward and backward modes`(): Unit {
        for (seed in 0L until 24L) for (index in listOf(1, 2, 3)) FiniteProgramFixtures().use { f ->
            val random = Random(seed)
            val fields = (0..2).map { f.field("f$it") }
            val prefix = List(random.nextInt(3)) { fields[random.nextInt(fields.size)] }
            val suffix = List(random.nextInt(3)) { fields[random.nextInt(fields.size)] }
            val path = prefix + suffix
            val cleanerPath = when ((seed % 4).toInt()) {
                0 -> emptyList()
                1 -> suffix
                2 -> suffix + fields[0]
                else -> listOf(fields.first { it != suffix.firstOrNull() })
            }
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = f.mark("U")
            val root = f.method("readRoot$seed", listOf(listOf(1), listOf(2), emptyList()), 2)
            val reader = f.method("reader$seed", listOf(listOf(1), emptyList()), 1)
            val caller = root.key("caller-$seed")
            val callee = reader.key("reader-$seed")
            val source = PathEdge(ZERO_FACT, PathFact(x, path, Tail.EXACT, t), ExclusionSet.Empty)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val cleaner = Cleaner(if (seed % 5 == 0L) y else ret, f.ap.path(cleanerPath),
                CleanReach.values()[(seed % 3).toInt()], if (seed % 2 == 0L) t.mark else u)
            f.call(caller, 1, call(callee, actions = listOf(CleanStep.Clean(cleaner))))
            f.statement(callee, 0, summary(starEdge(x, prefix, ret, emptyList())))
            val abstract = seed % 2 == 1L
            val entry = f.pattern(if (index == 2) ret else x,
                tail = Tail.ANY, mark = if (abstract) MarkSlot.STAR else t)
            val exit = f.pattern(if (index == 2) x else ret, tail = Tail.ANY, mark = t)
            val sink = SinkRule(rule("read-$seed"), 0, listOf(f.pattern(ret, suffix, mark = t)), emptyList())
            f.exit(caller, 2, ExitRules(RuleStatement(summary(keepEdge(ret), ZERO_KEEP),
                StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val demand = listOf(callee to DemandPattern(entry, exit),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(ret, tail = Tail.ANY, mark = t)))
            val seeds = if (index == 2) listOf<Seed>(Seed.Sink(sink.rule, caller, root.inst(2), sink.patterns.single()))
                else listOf<Seed>(Seed.Source(caller, root.inst(0), source))
            val config = f.config(index, L = maxOf(1, index - 1), roots = listOf(caller),
                demand = if (index == 1) emptyList() else demand, seeds = seeds)
            val actual = compare(f, config, seed, fields, listOf(t.mark, u))
            assertTrue(actual.processed.isNotEmpty())
            if (index != 1) assertTrue(actual.requests.isEmpty(), "Restricted runs never request marks or positions")
            if (index == 1 && path.size <= config.fieldLimit) assertContains(actual.links,
                ObservedLink(callee, caller, setOf(ZERO_PATTERN), Layer.DEMAND, root.inst(1), Layer.DEMAND,
                    GroupedScalarMarks(f.ap.marks).pattern(f.pattern(x, path, mark = t))), "seed=$seed index=$index full source path=$path")
            if (index == 3 && !abstract && path.size <= config.fieldLimit) assertContains(actual.rawSummaries,
                ObservedEdge(callee, setOf(f.pattern(x, path, mark = t)),
                    Conclusion(PathFact(ret, suffix, Tail.EXACT, t), ExclusionSet.Empty, false)),
                "seed=$seed index=$index full concrete read prefix=$prefix suffix=$suffix")
            if (index == 1 && prefix.isEmpty() && path.size <= config.fieldLimit && (cleaner.base == y || cleaner.mark == u))
                assertContains(actual.rawSummaries, ObservedEdge(caller, setOf(ZERO_PATTERN),
                    GroupedScalarMarks(f.ap.marks).conclusion(Conclusion(PathFact(ret, suffix, Tail.EXACT, t), ExclusionSet.Empty, false))),
                    "seed=$seed index=$index untouched concrete cleaner full return path=$suffix")
        }
    }

    @Test
    fun `generated static class and field reads preserve the complete path under mark changing edges`(): Unit {
        for (seed in 0L until 12L) for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Generated$seed"))
            val fields = listOf(f.field("static value"), f.field("nested"))
            val path = listOf(cls, fields[0]) + if (seed % 2 == 0L) emptyList() else listOf(fields[1])
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val root = f.method("staticRoot$seed", listOf(listOf(1), emptyList()), 1)
            val reader = f.method("staticReader$seed", listOf(listOf(1), emptyList()), 1)
            val caller = root.key()
            val callee = reader.key("static context")
            val source = PathEdge(ZERO_FACT, PathFact(AccessPathBase.ClassStatic, path, Tail.EXACT, t), ExclusionSet.Empty)
            f.entry(caller, RuleStatement(directStaticWrite(f, caller, 0, source, summary(ZERO_KEEP, source)), StatementSummary.EMPTY, emptyList()))
            f.call(caller, 0, call(callee, setOf(AccessPathBase.ClassStatic)))
            val read = PathEdge(PathFact(STATIC, path, Tail.EXACT, t), PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            f.statement(callee, 0, directStaticRead(f, callee, 0, read, summary(read)))
            val abstract = seed % 3 == 0L
            val d = DemandPattern(f.pattern(AccessPathBase.ClassStatic, path,
                if (abstract) Tail.ANY else Tail.EXACT, if (abstract) MarkSlot.STAR else t),
                f.pattern(ret, tail = if (abstract) Tail.ANY else Tail.EXACT, mark = u))
            val config = f.config(index, L = maxOf(1, index - 1), roots = listOf(caller),
                demand = if (index == 1) emptyList() else listOf(callee to d),
                seeds = listOf(Seed.Source(caller, root.inst(0), source)))
            // Direct F1 effects have their own operation-point contract. Later Full runs still
            // compare the entire ordinary scalar closure with actual query admission.
            val actual = if (index == 1) ProductionSchedule(f, config, policy = SchedulePolicy.SEEDED,
                seed = seed + 100L, quantum = 1, duplicateDeliveries = true).run().also {
                assertDirectStaticProtocol(it, f.ap.marks)
            } else compare(f, config, seed + 100L, listOf(cls) + fields, listOf(t.mark, u.mark))
            // Run-1 field group answers use a STAR premise; later ZERO premises and exact results stay unchanged.
            if ((index == 1 && path.size == 2) || (index != 1 && !abstract)) assertContains(actual.rawSummaries, ObservedEdge(caller, if (index == 1) setOf(GroupedScalarMarks(f.ap.marks).pattern(f.pattern(STATIC, path.take(2), Tail.STAR, t))) else setOf(ZERO_PATTERN),
                if (index == 1) GroupedScalarMarks(f.ap.marks).conclusion(Conclusion(PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false))
                else Conclusion(PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false)),
                "seed=$seed index=$index full static path=$path")
            if (index == 3 && abstract) {
                assertTrue(actual.requests.isEmpty())
                assertTrue(actual.rawSummaries.any { it.conclusion.fact.mark == u },
                    "Actual concrete input authorizes the transfer independently of an abstract old guide")
            }
        }
    }

    @Test
    fun `generated literal paths keep fixed targets with singleton run1 and exact later premises`(): Unit {
        for (seed in 0L until 12L) for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val random = Random(seed + 200)
            val fields = (0..2).map { f.field("joint$it") }
            val px = List(random.nextInt(3)) { fields[random.nextInt(3)] }
            val py = List(random.nextInt(3)) { fields[random.nextInt(3)] }
            val out = List(random.nextInt(3)) { fields[random.nextInt(3)] }
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val root = f.method("jointRoot$seed", listOf(listOf(1), emptyList()), 1)
            val join = f.method("joint$seed", listOf(listOf(1), emptyList()), 1)
            val caller = root.key()
            val callee = join.key("joint context")
            val sx = PathEdge(ZERO_FACT, PathFact(x, px, Tail.EXACT, t), ExclusionSet.Empty)
            val sy = PathEdge(ZERO_FACT, PathFact(y, py, Tail.EXACT, u), ExclusionSet.Empty)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, sx, sy), StatementSummary.EMPTY, emptyList()))
            f.call(caller, 0, call(callee, setOf(x, y)))
            val literals = listOf(f.pattern(x, px, mark = t), f.pattern(y, py, mark = u))
            val target = PathFact(ret, out, if (seed % 2 == 0L) Tail.EXACT else Tail.ANY_TAINT, t)
            f.statement(callee, 0, StatementSummary(setOf(x, y), emptyList(),
                listOf(ConjunctiveEdge(literals, target)), emptyMap()))
            val config = f.config(index, L = maxOf(1, index - 1), roots = listOf(caller),
                demand = if (index == 1) emptyList() else listOf(
                    callee to DemandPattern(literals[0], f.pattern(ret, out, Tail.ANY, t)),
                    callee to DemandPattern(literals[1], f.pattern(ret, out, Tail.ANY, t))),
                seeds = listOf(Seed.Source(caller, root.inst(0), sx), Seed.Source(caller, root.inst(0), sy)))
            val actual = compare(f, config, seed + 200L, fields, listOf(t.mark, u.mark))
            if (index == 1) {
                assertTrue(actual.selectedDemand.any { it.method == callee && it.conclusion.fact.base == ret &&
                    it.premise.size == 1 && it.premise.single().fact.base == x })
                assertTrue(actual.rawSummaries.all { it.premise.size == 1 }, "Run 1 has only atomic executable origins")
            } else assertTrue(actual.selectedDemand.any { it.method == callee && it.premise.size == 2 })
            // At run1's nonstatic root policy, a below-root mark answer has a STAR initial and an ANY starting fact in DEMAND
            // layer. A root exact answer is NORMAL. Restricted exact demand entries copy exact facts.
            val targetDemand = index == 1
            val expectedTail = target.tail
            val uncut = Conclusion(target.copy(tail = expectedTail), ExclusionSet.Empty, targetDemand)
            val expectedResult = limit(if (index == 1) GroupedScalarMarks(f.ap.marks).conclusion(uncut) else uncut, config.fieldLimit)
            assertContains(actual.rawSummaries, ObservedEdge(caller, setOf(ZERO_PATTERN), expectedResult),
                "seed=$seed index=$index px=$px py=$py out=$out target=$target expectedDemand=$targetDemand")
            if (index != 1) assertFalse(actual.rawSummaries.any {
                it.method == callee && it.premise.size == 1 && it.conclusion.fact.base == ret
            })
        }
    }
}
