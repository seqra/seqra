package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.fixtures.withRun1MarkMode
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.driver.*
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*

/** Actual actors and counted replies; expected values do not call the static service's selection helpers. */
class GroupedDirectStaticsEngineTest {
    private fun id(key: MethodKey, index: Int, f: DriverFixtures): OperationId =
        OperationId(FormKey(FormOwner(), key.method, key, f.program.inst(key, index)))

    private fun write(f: DriverFixtures, key: MethodKey, index: Int, edge: PathEdge): StatementSummary =
        f.summary(ZERO_KEEP, edge).withStaticOperations(listOf(
            StaticOperation.Write(id(key, index, f), Pattern(edge.to, ExclusionSet.Empty), edge)))

    private fun read(f: DriverFixtures, key: MethodKey, index: Int, edge: PathEdge): StatementSummary =
        f.summary(ZERO_KEEP, edge).withStaticOperations(listOf(StaticOperation.Read(id(key, index, f), edge)))

    private fun call(f: DriverFixtures, callee: MethodKey, result: AccessPathBase = AccessPathBase.LocalVar(0)): CallPlan {
        val pass = f.summary(ZERO_KEEP, keepEdge(STATIC), keepEdge(result))
        return CallPlan(setOf(STATIC, result, AccessPathBase.Return), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, pass),
            CallStage.Edges(BOUND, ADDED, SOURCES, pass),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Edges(RETURNED, AFTER, BIND_BACK,
                f.summary(ZERO_KEEP, keepEdge(STATIC), starEdge(AccessPathBase.Return, emptyList(), result, emptyList()))),
        ), emptyList(), BEFORE, AFTER)
    }

    private fun nestedGuardedWriter(oneCompatibleCall: Boolean, provideSecondInput: Boolean = true): Boolean {
        DriverFixtures().use { f ->
            val p = f.program
            val main = p.method("guard main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val relay = p.method("guard relay", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val writer = p.method("guard writer", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val g = p.mark("G"); val h = p.mark("H"); val u = p.mark("U")
            val x = AccessPathBase.LocalVar(0); val y = AccessPathBase.LocalVar(1)
            val a = AccessPathBase.Argument(0); val b = AccessPathBase.Argument(1)
            val field = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            fun source(base: AccessPathBase, mark: TaintMark) = PathEdge(ZERO_FACT,
                PathFact(base, emptyList(), Tail.EXACT, MarkSlot.Concrete(mark)), ExclusionSet.Empty)
            p.entry(main, RuleStatement(f.summary(ZERO_KEEP, source(x, g), source(y, h)), StatementSummary.EMPTY, emptyList()))
            fun bound(callee: MethodKey, bindings: List<Pair<AccessPathBase, AccessPathBase>>): CallPlan {
                val bases = bindings.map { it.first }.toSet() + STATIC
                return CallPlan(bases, listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN,
                        f.summary(ZERO_KEEP, keepEdge(STATIC), *bindings.map { starEdge(it.first, emptyList(), it.second, emptyList()) }.toTypedArray())),
                    CallStage.Edges(BOUND, ADDED, SOURCES, StatementSummary.identities(bindings.map { it.second } + STATIC + AccessPathBase.Zero)),
                    CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                    CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(ZERO_KEEP, keepEdge(STATIC))),
                ), emptyList(), BEFORE, AFTER)
            }
            p.call(main, 1, bound(relay, if (oneCompatibleCall) listOf(x to a, y to b) else listOf(x to a)))
            p.call(main, 2, bound(relay, if (provideSecondInput) listOf(y to b) else emptyList()))
            p.call(relay, 1, bound(writer, listOf(a to a, b to b)))
            val target = PathFact(STATIC, field, Tail.EXACT, MarkSlot.Concrete(u))
            val conjunction = ConjunctiveEdge(listOf(p.pattern(a, mark = MarkSlot.Concrete(g)),
                p.pattern(b, mark = MarkSlot.Concrete(h))), target)
            p.statement(writer, 1, StatementSummary(setOf(a, b), listOf(MicroEdge.of(ZERO_KEEP)), listOf(conjunction), emptyMap(),
                staticOperations = listOf(StaticOperation.Write(id(writer, 1, f), Pattern(target, ExclusionSet.Empty)))))
            val required = p.pattern(STATIC, field, mark = MarkSlot.Concrete(u))
            val sink = SinkRule(f.rule("joint static U"), 0, listOf(required), emptyList())
            val checks = StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(id(main, 3, f), required)))
            p.exit(main, 3, ExitRules(RuleStatement(checks, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val result = f.run(p.config(roots = listOf(main)), timeout = 20.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            return result.witnessesByKey().any { it.key.rule == sink.rule }
        }
    }

    @Test fun `run1 nested forwarding permits a candidate after separate callers complete the local guard`() {
        assertTrue(nestedGuardedWriter(oneCompatibleCall = false))
    }

    @Test fun `run1 nested forwarding cannot emit before its local guard is complete`() {
        assertFalse(nestedGuardedWriter(oneCompatibleCall = false, provideSecondInput = false))
    }

    @Test fun `nested ordinary forwarding retains a complete same caller guard binding`() {
        assertTrue(nestedGuardedWriter(oneCompatibleCall = true))
    }

    private data class SpatialResult(val candidate: Boolean, val finalFinding: Boolean, val reachedFull: Boolean)

    private fun unarySpatialWriter(actualField: String, recursive: Boolean = false,
                                   refine: Boolean = false): SpatialResult {
        DriverFixtures().use { f ->
            val p = f.program
            val main = p.method("spatial main", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val writer = p.method("spatial writer", if (recursive)
                listOf(listOf(1), listOf(2), listOf(3), emptyList()) else listOf(listOf(1), listOf(2), emptyList()),
                if (recursive) 3 else 2).key()
            val g = p.mark("spatial G"); val h = p.mark("spatial H")
            val local = AccessPathBase.LocalVar(0); val arg = AccessPathBase.Argument(0)
            val field = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), p.field("result"))
            val source = PathEdge(ZERO_FACT, p.pattern(local, listOf(p.field(actualField)), mark = MarkSlot.Concrete(g)).fact,
                ExclusionSet.Empty)
            p.entry(main, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            fun invoke(from: AccessPathBase) = CallPlan(setOf(STATIC, from, arg), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, keepEdge(STATIC), starEdge(from, emptyList(), arg, emptyList()))),
                CallStage.Edges(BOUND, ADDED, SOURCES, f.summary(ZERO_KEEP, keepEdge(STATIC), keepEdge(arg))),
                CallStage.Callees(ADDED, RETURNED, listOf(writer)),
                CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(ZERO_KEEP, keepEdge(STATIC))),
            ), emptyList(), BEFORE, AFTER)
            p.call(main, 1, invoke(local))
            if (recursive) p.call(writer, 2, invoke(arg))
            val target = p.pattern(STATIC, field, mark = MarkSlot.Concrete(h)).fact
            val form = FormKey(FormOwner(), writer.method, writer, p.inst(writer, 1))
            val id = RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0)
            val builder = RulePredicate.Builder(p.cancellation, p.ap.marks)
            val condition = builder.build(builder.leaf(p.pattern(arg, listOf(p.field("f")), mark = MarkSlot.Concrete(g))))
            val action = SourceAction(ActionId(id, ActionRole.SOURCE, 0), target)
            val grouped = RulePredicate.Builder(p.cancellation, p.ap.marks)
            val groupedCondition = grouped.build(grouped.leaf(p.pattern(arg, listOf(p.field("f")),
                mark = MarkSlot.Concrete(p.ap.marks.group(p.ap.marks.groupOf(g))))))
            val occurrence = SourceRuleOccurrence(id, condition, listOf(action), groupTransfers = true,
                unaryTransfers = listOf(SourceUnaryTransfer(groupedCondition, condition.leaves.single())))
            p.statement(writer, 1, StatementSummary(setOf(arg), listOf(MicroEdge.of(ZERO_KEEP), MicroEdge.of(keepEdge(arg))),
                emptyList(), emptyMap(), sources = listOf(occurrence),
                staticOperations = listOf(StaticOperation.Write(OperationId(form, action = action.id),
                    Pattern(target, ExclusionSet.Empty), action = action.id))))
            val required = p.pattern(STATIC, field, mark = MarkSlot.Concrete(h))
            val sink = SinkRule(f.rule("spatial writer result"), 0, listOf(required), emptyList())
            val checks = StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(this.id(main, 2, f), required)))
            p.exit(main, 2, ExitRules(RuleStatement(checks, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            if (!refine) {
                val result = f.run(p.config(roots = listOf(main)), timeout = 20.seconds)
                assertEquals(RunStatus.COMPLETE, result.status)
                val candidate = result.witnessesByKey().any { it.key.rule == sink.rule }
                assertTrue(result.witnessesByKey().filter { it.key.rule == sink.rule }.values.flatten()
                    .flatMap { it.edges }.all { it.facts.layer == Layer.DEMAND })
                return SpatialResult(candidate, false, false)
            }
            val report = withRun1MarkMode(MarkMode.GROUP) {
                f.driver(FixedLimits(listOf(1, 1, 2)), 20.seconds)
            }.analyze(listOf(main))
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            return SpatialResult(f.results.first().witnessesByKey().any { it.key.rule == sink.rule },
                report.entries.any { it.key.rule == sink.rule }, f.results.any { it.runIndex > 1 })
        }
    }

    @Test fun `ordinary canonical input permits a sibling field DEMAND candidate`() {
        assertTrue(unarySpatialWriter("h").candidate)
        assertTrue(unarySpatialWriter("f").candidate)
    }

    @Test fun `later FULL refinement rejects the sibling candidate and retains the matching source`() {
        val sibling = unarySpatialWriter("h", refine = true)
        assertTrue(sibling.candidate)
        assertTrue(sibling.reachedFull)
        assertFalse(sibling.finalFinding)
        val matching = unarySpatialWriter("f", refine = true)
        assertTrue(matching.candidate)
        assertTrue(matching.reachedFull)
        assertTrue(matching.finalFinding)
    }

    @Test fun `rooted recursive ordinary summaries reach a finite unchanged binding`() {
        assertTrue(unarySpatialWriter("f", recursive = true).candidate)
    }

    @Test fun `early setter and getter cross two ZERO wrappers and refine through backward run two`() {
        DriverFixtures().use { f ->
            val p = f.program
            val main = p.method("main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val setter = p.method("setter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val wrap = p.method("wrap", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val deep = p.method("deep", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val getter = p.method("getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val field = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            val full = p.mark("source T")
            val source = PathEdge(ZERO_FACT, PathFact(STATIC, field, Tail.EXACT, MarkSlot.Concrete(full)), ExclusionSet.Empty)
            p.statement(setter, 1, write(f, setter, 1, source))
            p.statement(getter, 1, read(f, getter, 1, starEdge(STATIC, field, AccessPathBase.Return, emptyList())))
            p.call(main, 1, call(f, setter)); p.call(main, 2, call(f, wrap))
            p.call(wrap, 1, call(f, deep)); p.call(deep, 1, call(f, getter))
            val local = AccessPathBase.LocalVar(0)
            val sink = SinkRule(f.rule("wrapped getter"), 0, listOf(p.pattern(local, mark = MarkSlot.Concrete(full))), emptyList())
            p.exit(deep, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val report = withRun1MarkMode(MarkMode.GROUP) {
                f.driver(FixedLimits(listOf(1, 1, 2)), 20.seconds)
            }.analyze(listOf(main))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertTrue(f.results.first().witnessesByKey().any { it.key.rule == sink.rule }, "F1 conditional static return reaches the sink")
            assertTrue(f.results.any { it.runIndex == 2 && it.analyzers.any { actor -> actor.sourceHits?.occurrences()?.any() == true } },
                "B2 reaches the actual full source occurrence")
            assertTrue(report.entries.any { it.key.rule == sink.rule })
            assertTrue(f.results.any { result -> result.runIndex == 3 && result.witnessesByKey().any { (key, witnesses) ->
                key.rule == sink.rule && witnesses.any { witness -> witness.edges.any { edge ->
                    f.ops.leaves(edge.facts).any { it.fact.mark == MarkSlot.Concrete(full) }
                } }
            } }, "Later refinement checks the original full mark")
            assertTrue(f.processed.none { it.run == 1 && it.delta.facts.base == STATIC })
            assertTrue(f.results.first().analyzers.flatMap { it.summaries.all() }.none { (initial, _) ->
                initial.isFlow && initial.member(0).base == STATIC && initial.member(0).path == null
            }, "There is no executable S-root default")
        }
    }

    @Test fun `a read before its writer resumes locally without a static entry analysis`() {
        DriverFixtures().use { f ->
            val p = f.program
            val m = p.method("reader before writer", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = listOf(cls, p.field("f"))
            val t = p.mark("T")
            val local = AccessPathBase.LocalVar(0)
            val source = PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty)
            p.statement(m, 0, read(f, m, 0, starEdge(STATIC, path, local, emptyList())))
            p.statement(m, 1, write(f, m, 1, source))
            val sink = SinkRule(f.rule("late field"), 0, listOf(p.pattern(local, mark = MarkSlot.Concrete(t))), emptyList())
            p.exit(m, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val result = f.run(p.config(roots = listOf(m)))
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(result.witnessesByKey().any { it.key.rule == sink.rule })
            assertTrue(f.processed.any { it.run == 1 && it.delta.premise.members.any { member -> member.base == STATIC } && it.delta.facts.base == local })
            assertTrue(f.processed.none { it.run == 1 && it.delta.facts.base == STATIC }, "Static values never traverse ordinary successor queues")
            assertTrue(result.analyzers.flatMap { it.summaries.all() }.any { (premise, value) -> premise.isZero && value.base == STATIC && value.layer == Layer.DEMAND })
        }
    }

    @Test fun `a reached source publishes before return without static successor propagation`() {
        DriverFixtures().use { f ->
            val p = f.program
            val m = p.method("early writer", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), p.field("f"))
            val t = p.mark("T")
            val source = PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty)
            p.statement(m, 0, write(f, m, 0, source))
            val result = f.run(p.config(roots = listOf(m)))
            assertEquals(RunStatus.COMPLETE, result.status)
            val publications = result.analyzers.flatMap { it.summaries.all() }
            assertTrue(publications.any { (original, value) -> original.isZero && value.base == STATIC && value.layer == Layer.DEMAND })
            assertFalse(publications.any { (original, value) -> original.isFlow && value.base == STATIC })
            assertTrue(f.processed.none { it.delta.facts.base == STATIC })
        }
    }

    @Test fun `a static copy cycle without a source cannot fabricate a group`() {
        DriverFixtures().use { f ->
            val p = f.program
            val m = p.method("unrooted cycle", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val a = listOf(cls, p.field("a")); val b = listOf(cls, p.field("b"))
            val t = p.mark("T")
            fun copy(index: Int, from: List<Int>, to: List<Int>): StatementSummary {
                val edge = starEdge(STATIC, from, STATIC, to)
                val op = id(m, index, f)
                return f.summary(ZERO_KEEP, edge).withStaticOperations(listOf(
                    StaticOperation.Read(op, edge), StaticOperation.Write(op, Pattern(edge.to, ExclusionSet.Empty), edge)))
            }
            p.statement(m, 0, copy(0, a, b)); p.statement(m, 1, copy(1, b, a))
            val sink = SinkRule(f.rule("unrooted"), 0, listOf(p.pattern(STATIC, a, mark = MarkSlot.Concrete(t))), emptyList())
            p.exit(m, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val result = f.run(p.config(roots = listOf(m)))
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(result.witnessesByKey().none { it.key.rule == sink.rule })
            assertTrue(result.analyzers.flatMap { it.summaries.all() }.none { (_, value) -> value is TaintTree && value.base == STATIC })
        }
    }

    @Test fun `actual caller marks apply FLOW writers and static copies without a marked callee start`() {
        DriverFixtures().use { f ->
            val p = f.program
            val main = p.method("flow bootstrap main", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val setter = p.method("flow bootstrap setter", listOf(listOf(1), emptyList()), 1).key()
            val copy = p.method("flow bootstrap copy", listOf(listOf(1), emptyList()), 1).key()
            val local = AccessPathBase.LocalVar(0); val arg = AccessPathBase.Argument(0)
            val full = p.mark("flow bootstrap T")
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val from = listOf(cls, p.field("from")); val target = listOf(cls, p.field("target"))
            val source = PathEdge(ZERO_FACT, p.pattern(local, mark = MarkSlot.Concrete(full)).fact, ExclusionSet.Empty)
            p.entry(main, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val store = starEdge(arg, emptyList(), STATIC, from)
            p.statement(setter, 0, write(f, setter, 0, store))
            val transfer = starEdge(STATIC, from, STATIC, target)
            val copyId = id(copy, 0, f)
            p.statement(copy, 0, f.summary(ZERO_KEEP, transfer).withStaticOperations(listOf(
                StaticOperation.Read(copyId, transfer),
                StaticOperation.Write(copyId, Pattern(transfer.to, ExclusionSet.Empty), transfer))))
            p.call(main, 1, CallPlan(setOf(STATIC, local, arg), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN,
                    f.summary(ZERO_KEEP, keepEdge(STATIC), starEdge(local, emptyList(), arg, emptyList()))),
                CallStage.Edges(BOUND, ADDED, SOURCES, f.summary(ZERO_KEEP, keepEdge(STATIC), keepEdge(arg))),
                CallStage.Callees(ADDED, RETURNED, listOf(setter)),
                CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(ZERO_KEEP, keepEdge(STATIC))),
            ), emptyList(), BEFORE, AFTER))
            p.call(main, 2, call(f, copy))
            val wanted = p.pattern(STATIC, target, mark = MarkSlot.Concrete(full))
            val sink = SinkRule(f.rule("flow bootstrap sink"), 0, listOf(wanted), emptyList())
            p.exit(main, 3, ExitRules(RuleStatement(StatementSummary.EMPTY.withStaticOperations(listOf(
                StaticOperation.MarkInput(id(main, 3, f), wanted))), StatementSummary.EMPTY, listOf(sink)),
                false, emptySet()))
            val result = f.run(p.config(roots = listOf(main)), timeout = 20.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(result.witnessesByKey().any { it.key.rule == sink.rule })
            assertTrue(result.analyzers.single { it.key == setter }.summaries.all().any { (original, value) ->
                original.isFlow && original.member(0).base == arg && value.base == STATIC
            }, "The ordinary FLOW writer must be published")
            assertTrue(result.analyzers.single { it.key == copy }.summaries.all().none { (original, value) ->
                !original.isZero && original.members.all { it.base == STATIC } && value.base == STATIC
            }, "The actual copy supplies the observed sink without a retained static/static row")
            assertTrue(f.processed.none { it.run == 1 && it.delta.facts.base == STATIC })
        }
    }
}
