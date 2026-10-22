package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.demandProbe
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.engine.historical.isSpecialStaticRoot
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.fixtures.directStaticWrite
import org.opentaint.dataflow.bidi.fixtures.directStaticRead
import org.opentaint.dataflow.bidi.fixtures.GroupedScalarMarks
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.Record
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Exercise the actual driver, caller subscriptions, return continuations and native reader. */
class Run1StaticDriverSafetyTest {
    private data class Processed(val run: Int, val method: MethodKey, val delta: EdgeDelta)
    private data class ReadRecord(val run: Int, val part: Subscription, val record: Record,
                                  val run1ServiceAbsent: Boolean)
    private data class ReadSummary(val run: Int, val part: Subscription, val publication: Publication)
    private data class ReadView(val run: Int, val part: Subscription, val view: RecordLeafView)
    private class Observation {
        val configs = arrayListOf<RunConfig>()
        val views = ConcurrentLinkedQueue<ReadView>()
        val results = arrayListOf<RunResult<RunMethodAnalyzer>>()
        val processed = ConcurrentLinkedQueue<Processed>()
        val records = ConcurrentLinkedQueue<ReadRecord>()
        val summaries = ConcurrentLinkedQueue<ReadSummary>()
        val invoked = arrayListOf<Int>()
    }

    private fun execute(f: DriverFixtures, root: MethodKey, limits: List<Int>, replayBackward: Boolean = false): Pair<Report, Observation> {
        val seen = Observation()
        f.program.freshShared().use { owner ->
            fun invoke(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> {
                    seen.configs += config
                    seen.invoked += config.index
                    val actual = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                        MethodCallServicesFactory { actor ->
                            val service = RunMethodCallServices(actor)
                            object : MethodCallServices by service {
                                override fun applySummary(part: Subscription, pub: Publication, member: Int) {
                                    seen.summaries += ReadSummary(config.index,part,pub)
                                    service.applySummary(part,pub,member)
                                }
                                override fun applyRecordView(part: Subscription, view: RecordLeafView) {
                                    seen.views += ReadView(config.index, part, view)
                                    service.applyRecordView(part, view)
                                }
                                override fun applyRecord(part: Subscription, record: Record) {
                                    seen.records += ReadRecord(config.index, part, record,
                                        actor.runnerPort.staticService == null)
                                    service.applyRecord(part, record)
                                }
                            }
                        })
                    val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                        actual.create(object : RunnerPort<RunMethodAnalyzer> by port {
                            override val onProcess: (MethodKey, EdgeDelta) -> Unit = { method, delta ->
                                seen.processed += Processed(config.index, method, delta)
                                port.onProcess?.invoke(method, delta)
                            }
                        })
                    }
                    return RunManager(config, shared, factory, quantum = 1).run(timeout).also { seen.results += it }
            }
            val driver = IterationDriver(FixedLimits(limits), owner, 20.seconds,
                invokeRun = ::invoke, guardBarrier = { _, body -> body() })
            val report = driver.analyze(listOf(root))
            if (replayBackward) {
                val backward = seen.configs.single { it.index == 2 }
                // A fresh actual B4 engine reads completed F3 native records,
                // independently of the driver's forward stop decision. Its
                // demand comes from F3; the R1-to-B2 completion policy does not
                // belong to B4. Keep the explicit sink seeds for this forced replay.
                val forward = seen.configs.single { it.index == 3 }
                val laterDemand = HandOff.of(forward, seen.results.single { it.runIndex == 3 },
                    ReportBuilder(), owner).demand
                invoke(RunConfig(4, 3, laterDemand,
                    owner.records.view(), backward.seeds, listOf(root)), owner, 20.seconds)
            }
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertTrue(seen.results.all { it.status == RunStatus.COMPLETE })
            assertTrue(seen.processed.none { it.run > 1 && it.delta.facts is FlowTree },
                "Actual ZERO/concrete seeds and concrete query starts cannot execute a broad FLOW guide")
            return report to seen
        }
    }

    private fun call(f: DriverFixtures, callee: MethodKey? = null, steps: List<CleanStep> = emptyList()): CallPlan {
        val identity = f.summary(keepEdge(STATIC), ZERO_KEEP)
        return CallPlan(setOf(STATIC), buildList {
            add(CallStage.Edges(BEFORE, BOUND, BIND_IN, identity))
            add(CallStage.Actions(BOUND, ADDED, steps))
            if (callee == null) add(CallStage.Edges(ADDED, RETURNED, UNRESOLVED, identity))
            else add(CallStage.Callees(ADDED, RETURNED, listOf(callee)))
            add(CallStage.Actions(RETURNED, REWRITTEN, emptyList()))
            add(CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, identity))
        }, emptyList(), BEFORE, AFTER)
    }

    private fun source(f: DriverFixtures, root: MethodKey, vararg targets: PathFact) {
        val edges = targets.map { PathEdge(ZERO_FACT, it, ExclusionSet.Empty) }
        val writes = edges.mapIndexed { ordinal, edge -> StaticOperation.Write(
            OperationId(FormKey(FormOwner(), root.method, root, f.program.inst(root, 0)), ordinal),
            Pattern(edge.to, ExclusionSet.Empty), edge) }
        f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP, *edges.toTypedArray()).withStaticOperations(writes),
            StatementSummary.EMPTY, emptyList()))
    }

    private fun sink(f: DriverFixtures, id: String, path: List<Int>, mark: TaintMark, tail: Tail = Tail.EXACT): SinkRule =
        SinkRule(f.rule(id),0,listOf(f.program.pattern(STATIC,path,tail,MarkSlot.Concrete(mark))),emptyList())

    private fun staticExit(f: DriverFixtures, owner: MethodKey, index: Int, vararg sinks: SinkRule): ExitRules {
        val checks = sinks.flatMap { it.patterns }.mapIndexed { ordinal, pattern ->
            StaticOperation.MarkInput(OperationId(FormKey(FormOwner(), owner.method, owner,
                f.program.inst(owner, index)), ordinal), pattern)
        }
        return ExitRules(RuleStatement(StatementSummary.EMPTY.withStaticOperations(checks), StatementSummary.EMPTY,
            sinks.toList()), false, emptySet())
    }

    private fun hasGroupWitness(f: DriverFixtures, seen: Observation, rule: SinkRule): Boolean {
        val first = seen.results.first()
        return first.witnessesByKey().any { (key, witnesses) -> key.rule == rule.rule && witnesses.any { witness ->
            witness.edges.any { edge -> edge.layer == Layer.DEMAND && f.ops.leaves(edge.facts).any { leaf ->
                (leaf.fact.mark as? MarkSlot.Concrete)?.let { f.manager.marks.isGroup(it.mark) } == true
            } }
        } }
    }

    private fun hasTaint(f: DriverFixtures, rows: Collection<Processed>, run: Int, method: MethodKey,
                         index: Int, path: List<Int>, mark: TaintMark): Boolean = rows.any { row ->
        row.run == run && row.method == method && row.delta.node.location.index == index &&
            row.delta.facts is TaintTree && f.ops.leaves(row.delta.facts).any {
                it.fact.path == path && it.fact.mark == MarkSlot.Concrete(mark)
            }
    }

    @Test fun `F1 cleaner candidates refine strongly without restoring T and preserve U`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("clean root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("clean callee",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val field = p.field("f")
            val path = listOf(cls,field); val t = p.mark("T"); val u = p.mark("U")
            source(f,root,p.pattern(STATIC,path,mark=MarkSlot.Concrete(t)).fact,
                p.pattern(STATIC,path,mark=MarkSlot.Concrete(u)).fact)
            p.call(root,1,call(f,callee))
            p.call(callee,1,call(f,steps=listOf(CleanStep.Clean(
                Cleaner(STATIC,f.manager.path(path),CleanReach.EXACT,t)))))
            val removed = sink(f,"removed T",path,t); val kept = sink(f,"kept U",path,u)
            p.exit(root,2,staticExit(f,root,2,removed,kept))
            val (report,seen) = execute(f,root,listOf(1,1,2))
            assertFalse(report.entries.any { it.key.rule == removed.rule })
            assertTrue(report.entries.any { it.key.rule == kept.rule })
            assertTrue(hasGroupWitness(f, seen, removed), "F1 selected-mark cleaning preserves the Group candidate")
            assertFalse(hasTaint(f,seen.processed,3,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,3,root,2,path,u))
            assertTrue(seen.results.first().witnessesByKey().any { it.key.rule == removed.rule })
            assertTrue(seen.processed.none { it.run == 1 && it.delta.premise is InitialAp &&
                isSpecialStaticRoot(it.delta.premise as InitialAp) }, "A stored default is not a root analysis")
        }
    }

    @Test fun `F1 writing default refines to a strong f kill and unchanged sibling g`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("write root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("write callee",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = p.field("f"); val sibling = p.field("g"); val t = p.mark("T")
            val fp = listOf(cls,field); val gp = listOf(cls,sibling)
            source(f,root,p.pattern(STATIC,fp,mark=MarkSlot.Concrete(t)).fact,
                p.pattern(STATIC,gp,mark=MarkSlot.Concrete(t)).fact)
            p.call(root,1,call(f,callee))
            p.statement(callee,1,MicroEdgeBuilder().apply {
                write(STATIC,fp,weak=false,values=emptyList(),aliasPaths=emptyList())
            }.build().withStaticOperations(listOf(StaticOperation.Write(
                OperationId(FormKey(FormOwner(), callee.method, callee, p.inst(callee, 1))),
                p.pattern(STATIC, fp, Tail.STAR, MarkSlot.STAR)))))
            val killed = sink(f,"written f",fp,t); val preserved = sink(f,"untouched g",gp,t)
            p.exit(root,2,staticExit(f,root,2,killed,preserved))
            val (report,seen) = execute(f,root,listOf(1,1,2))
            assertFalse(report.entries.any { it.key.rule == killed.rule })
            assertTrue(report.entries.any { it.key.rule == preserved.rule })
            assertTrue(hasGroupWitness(f, seen, killed), "F1 global static supply is provisional across an overwrite")
            assertFalse(hasTaint(f,seen.processed,3,root,2,fp,t))
            assertTrue(hasTaint(f,seen.processed,3,root,2,gp,t))
            assertTrue(seen.results.first().witnessesByKey().any { it.key.rule == killed.rule })
            assertTrue(checkNotNull(seen.configs.single { it.index == 2 }.demand)
                .near(callee, demandProbe(STATIC, fp, MarkSlot.Concrete(t))).any { it.entry.fact.path.isEmpty() &&
                    it.entry.fact.mark == GroupedScalarMarks(f.manager.marks).slot(MarkSlot.Concrete(t)) },
                "The shared Group permission retains actual Full marks and never starts FLOW")
            assertTrue(seen.results.filter { it.runIndex > 1 }.all { result ->
                result.analyzers.single { it.key == callee }.nonZeroInitial
            }, "Strong actual queries start even when F1 had no read request")
        }
    }

    @Test fun `F1 class-point candidates refine to the exact T kill and preserve U`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("point clean root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("point clean callee",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val path = listOf(cls)
            val t = p.mark("T"); val u = p.mark("U")
            source(f,root,p.pattern(STATIC,path,Tail.EXACT,MarkSlot.Concrete(t)).fact,
                p.pattern(STATIC,path,Tail.EXACT,MarkSlot.Concrete(u)).fact)
            p.call(root,1,call(f,callee))
            p.call(callee,1,call(f,steps=listOf(CleanStep.Clean(
                Cleaner(STATIC,f.manager.path(path),CleanReach.EXACT,t)))))
            val removed = sink(f,"removed point T",path,t); val kept = sink(f,"kept point U",path,u)
            p.exit(root,2,staticExit(f,root,2,removed,kept))
            val (report,seen) = execute(f,root,listOf(1,1,2))
            assertFalse(report.entries.any { it.key.rule == removed.rule },
                "Strong class-point execution cannot regain cleaned T from a provisional F1 default")
            assertTrue(report.entries.any { it.key.rule == kept.rule },
                "Direct class starts must include actual U although only T is named by the cleaner")
            assertTrue(hasGroupWitness(f, seen, removed), "F1 selected-mark cleaning preserves the Group candidate")
            assertFalse(hasTaint(f,seen.processed,3,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,3,root,2,path,u))
        }
    }

    @Test fun `broad class chain keeps ordinary PART demand residual without a propagated F1 root`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("broad point root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("broad point callee",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val path = listOf(cls)
            val t = p.mark("T"); val u = p.mark("U")
            // A finite broad supplier exercises the allowed input codec. It is
            // not a valid JVM source target: that adapter permits exact C only.
            source(f,root,p.pattern(STATIC,path,Tail.ANY_TAINT,MarkSlot.Concrete(t)).fact,
                p.pattern(STATIC,path,Tail.ANY_TAINT,MarkSlot.Concrete(u)).fact)
            p.call(root,1,call(f,callee))
            p.call(callee,1,call(f,steps=listOf(CleanStep.Clean(
                Cleaner(STATIC,f.manager.path(path),CleanReach.EXACT,t)))))
            val residual = sink(f,"broad point T candidate",path,t); val kept = sink(f,"broad point U",path,u)
            p.exit(root,2,staticExit(f,root,2,residual,kept))
            val (report,seen) = execute(f,root,listOf(1))
            assertEquals(ReportState.DEMAND, report.entries.single { it.key.rule == residual.rule }.state)
            assertEquals(ReportState.DEMAND, report.entries.single { it.key.rule == kept.rule }.state)
            assertTrue(hasGroupWitness(f, seen, residual) && hasGroupWitness(f, seen, kept))
            assertTrue(seen.processed.none { it.run == 1 && it.delta.facts.base == STATIC })
            assertTrue(seen.processed.none { it.run == 1 && it.delta.node.location.index == 0 &&
                it.delta.premise.members.any { member -> member.base == STATIC } },
                "Static descriptors resume at reached operation points, not method entries")
            assertTrue(seen.results.single().analyzers.any { it.nonZeroInitial },
                "The logical nonzero counter also includes operation-point resumes")
            val early = seen.results.single().analyzers.single { it.key == root }.summaries.all().toList()
            for (full in listOf(t, u)) assertTrue(early.any { (premise, facts) -> premise.isZero &&
                facts.base == STATIC && facts.layer == Layer.DEMAND && f.ops.leaves(facts).any {
                    it.fact.mark == GroupedScalarMarks(f.manager.marks).slot(MarkSlot.Concrete(full))
                } }, "Every actual broad supply has its own rooted early effect")

        }
    }

    @Test fun `ZERO discovery keeps only a provisional F1 candidate after a nonreturning call`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("noreturn root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("noreturn callee",listOf(listOf(0),emptyList()),1).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val field = p.field("g")
            val path = listOf(cls,field); val t = p.mark("T")
            source(f,root,p.pattern(STATIC,path,mark=MarkSlot.Concrete(t)).fact)
            p.call(root,1,call(f,callee))
            val after = sink(f,"after nonreturn",path,t)
            p.exit(root,2,staticExit(f,root,2,after))
            val (report,seen) = execute(f,root,listOf(1,1,2))
            assertFalse(report.entries.any { it.key.rule == after.rule })
            assertEquals(listOf(1, 2, 3), seen.invoked)
            assertTrue(hasGroupWitness(f, seen, after),
                "Baseline ZERO discovery reaches the operation, so the global F1 supply is provisional")
            assertTrue(seen.results.drop(1).all { it.witnessesByKey().isEmpty() },
                "Full refinement must not preserve the provisional after-call candidate")
            assertTrue(seen.results.first().analyzers.single { it.key == root }.summaries.all().any { (premise, facts) ->
                premise.isZero && facts.base == STATIC && facts.layer == Layer.DEMAND
            }, "The source effect is published before the nonreturning call")
            assertTrue(seen.results.all { run -> run.analyzers.single { it.key == callee }.summaries.all().none() },
                "ZERO discovery never supplies a normal callee-return summary")
        }
    }

    @Test fun `eligible normal records are reusable and actual queries use the later Full schedule`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("record root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("record callee",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = p.field("f"); val sibling = p.field("g"); val deeper = p.field("h"); val t = p.mark("T")
            val fp = listOf(cls,field); val gp = listOf(cls,sibling)
            // At L1 this exact source cuts to g.ANY, forcing the real B2/F3 handoff.
            source(f,root,p.pattern(STATIC,gp+deeper,mark=MarkSlot.Concrete(t)).fact)
            p.call(root,1,call(f,callee))
            p.statement(callee,1,MicroEdgeBuilder().apply {
                write(STATIC,fp,weak=false,values=emptyList(),aliasPaths=emptyList())
            }.build().withStaticOperations(listOf(StaticOperation.Write(
                OperationId(FormKey(FormOwner(), callee.method, callee, p.inst(callee, 1))),
                p.pattern(STATIC, fp, Tail.STAR, MarkSlot.STAR)))))
            // F1 still cuts the source, but the sink's exact deep requirement
            // is coarse at B2/L1, then exact at F3/L2 and produces a reusable NORMAL record.
            val target = sink(f,"record survivor",gp+deeper,t)
            p.exit(root,2,staticExit(f,root,2,target))
            val (report,seen) = execute(f,root,listOf(1,1,2),replayBackward=true)
            assertEquals(listOf(1,2,3,4),seen.invoked)
            assertEquals(3,report.end.run)
            assertTrue(seen.records.filter { it.run == 2 }.none { it.record.conclusion is TaintTree },
                "Group taint effects are D and cannot become NORMAL records; ordinary eligible F1 records remain allowed")
            assertTrue(seen.results.filter { it.runIndex in 2..3 }.all { result ->
                result.analyzers.single { it.key == callee }.nonZeroInitial
            }, "Actual callee queries are independent of missing F1 static field summaries")
            val exact = p.pattern(STATIC, gp + deeper, mark = MarkSlot.Concrete(t))
            assertTrue(seen.views.any { it.run == 4 && it.part.callee == callee &&
                it.view.premise == exact && it.view.result == Conclusion(exact.fact, ExclusionSet.Empty, false) },
                "B4 reuses the exact NORMAL F3 record through its ordinary reversed read view")
            assertTrue(seen.configs.single { it.index == 4 }.records.byEntry(callee, exact).any { record ->
                record.direction == Direction.FORWARD && record.premise.toPattern() == exact &&
                    record.conclusion.layer == Layer.NORMAL && exact in f.ops.leaves(record.conclusion).toSet()
            }, "The transient view is backed by an actual persisted F3 native record")
            val backward = seen.results.single { it.runIndex == 2 }.analyzers.single { it.key == root }
            assertTrue(checkNotNull(backward.sourceHits).entries().any { (method, node, edge) ->
                method == root && node == p.inst(root, 0) && edge.to == exact.fact
            }, "The coarse B2 query still finds the exact primitive source edge")
            assertTrue(hasTaint(f,seen.processed,3,root,2,gp+deeper,t))
            assertTrue(report.entries.any { it.key.rule == target.rule })
        }
    }

    @Test fun `later strong root queries remain legal and can have a broad demand conclusion`() {
        DriverFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val field = f.program.field("f")
            val initial = f.manager.initial(STATIC,null,Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR)
            val keep = keepEdge(STATIC,listOf(cls),ExclusionSet.of(field))
            val restricted = CollectingOut()
            f.ops.applyEdge(f.ops.startFact(initial),initial,keep,true,
                ApMode(false,Direction.BACKWARD,2),restricted)
            val result = assertIs<FlowTree>(restricted.results.single())
            assertEquals(Layer.DEMAND,result.layer)
            assertFalse(result.root.child(cls)!!.leaf.isEmpty)
            // Later current-query closure permits broad static roots. The F1
            // initial prohibition cannot be used to discard this computed result.
        }
    }

    @Test fun `nested mixed-base actual queries reach the B2 source and authorize its exact F3 source edge`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("mixed root", listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
            val middle = p.method("mixed middle", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val leaf = p.method("mixed leaf", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val fp = listOf(cls, p.field("f")); val gp = listOf(cls, p.field("g"))
            val t = p.mark("T"); val mark = MarkSlot.Concrete(t)
            val x = AccessPathBase.LocalVar(0); val y = AccessPathBase.LocalVar(1)
            val arg = AccessPathBase.Argument(0); val ret = AccessPathBase.Return
            val gen = PathEdge(ZERO_FACT, p.pattern(STATIC, fp, mark = mark).fact, ExclusionSet.Empty)
            p.entry(root, RuleStatement(directStaticWrite(p, root, 0, gen, f.summary(ZERO_KEEP, gen)), StatementSummary.EMPTY, emptyList()))
            val read = starEdge(STATIC, fp, x, emptyList())
            p.statement(root, 1, directStaticRead(p, root, 1, read, MicroEdgeBuilder().apply { read(x, STATIC, fp) }.build()))

            fun bound(callee: MethodKey, from: AccessPathBase, to: AccessPathBase): CallPlan =
                CallPlan(setOf(STATIC, from, to), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN,
                        f.summary(keepEdge(STATIC), ZERO_KEEP, starEdge(from, emptyList(), arg, emptyList()))),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                    CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                        f.summary(keepEdge(STATIC), ZERO_KEEP, starEdge(ret, emptyList(), to, emptyList()))),
                ), emptyList(), BEFORE, AFTER)

            p.call(root, 2, bound(middle, x, y))
            p.call(middle, 1, bound(leaf, arg, ret))
            p.statement(leaf, 1, MicroEdgeBuilder().apply { move(ret, arg) }.build())
            val write = starEdge(y, emptyList(), STATIC, gp)
            p.statement(root, 3, directStaticWrite(p, root, 3, write, MicroEdgeBuilder().apply {
                write(STATIC, gp, weak = false, values = listOf(y), aliasPaths = emptyList())
            }.build()))
            val target = sink(f, "nested mixed target", gp, t)
            p.exit(root, 4, staticExit(f, root, 4, target))
            val (report, seen) = execute(f, root, listOf(1, 1, 2))
            assertEquals(listOf(1, 2, 3), seen.invoked)
            val backward = seen.results.single { it.runIndex == 2 }
            val hits = backward.analyzers.flatMap { checkNotNull(it.sourceHits).entries().toList() }
            assertTrue(hits.any { it.first == root && it.second == p.inst(root, 0) && it.third == gen })
            val forward = seen.configs.single { it.index == 3 }
            assertTrue(forward.seeds.allowsSource(root, p.inst(root, 0), gen),
                "Actual B2 source hits supply the exact source gate; current queries do not bypass it")
            assertTrue(seen.results.filter { it.runIndex > 1 }.all { result ->
                listOf(middle, leaf).all { key -> result.analyzers.single { it.key == key }.nonZeroInitial }
            })
            assertTrue(hasTaint(f, seen.processed, 3, root, 4, gp, t))
            assertTrue(report.entries.any { it.key.rule == target.rule })
        }
    }
}
