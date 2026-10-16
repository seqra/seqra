package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
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
                                  val catalogReleased: Boolean, val footprintPresent: Boolean)
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
                    if (!config.run1) assertNull(shared.run1StaticProfiles,
                        "The real completed-run barrier releases the request catalog")
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
                                        shared.run1StaticProfiles == null,
                                        shared.staticFootprint(part.callee.method) != null)
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
                // A fresh actual B4 engine reads completed B2 native records,
                // independently of the driver's forward stop decision.
                invoke(RunConfig(4, maxOf(4, backward.fieldLimit), backward.demand,
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
        f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP,
            *targets.map { PathEdge(ZERO_FACT,it,ExclusionSet.Empty) }.toTypedArray()),
            StatementSummary.EMPTY,emptyList()))
    }

    private fun sink(f: DriverFixtures, id: String, path: List<Int>, mark: TaintMark, tail: Tail = Tail.EXACT): SinkRule =
        SinkRule(f.rule(id),0,listOf(f.program.pattern(STATIC,path,tail,MarkSlot.Concrete(mark))),emptyList())

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
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(removed,kept)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3))
            assertFalse(report.entries.any { it.key.rule == removed.rule })
            assertTrue(report.entries.any { it.key.rule == kept.rule })
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,t), "F1 default is provisional")
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
            }.build())
            val killed = sink(f,"written f",fp,t); val preserved = sink(f,"untouched g",gp,t)
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(killed,preserved)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3))
            assertFalse(report.entries.any { it.key.rule == killed.rule })
            assertTrue(report.entries.any { it.key.rule == preserved.rule })
            assertTrue(hasTaint(f,seen.processed,1,root,2,fp,t), "The F1 writing default is coarse")
            assertFalse(hasTaint(f,seen.processed,3,root,2,fp,t))
            assertTrue(hasTaint(f,seen.processed,3,root,2,gp,t))
            assertTrue(seen.results.first().witnessesByKey().any { it.key.rule == killed.rule })
            assertTrue(checkNotNull(seen.configs.single { it.index == 2 }.demand)
                .near(callee, STATIC, null).any { it.entry.fact.path.isEmpty() &&
                    it.entry.fact.mark == MarkSlot.STAR },
                "A broad F1 root guide exists, but cannot become a strong FLOW start")
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
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(removed,kept)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3))
            assertFalse(report.entries.any { it.key.rule == removed.rule },
                "Strong class-point execution cannot regain cleaned T from a provisional F1 default")
            assertTrue(report.entries.any { it.key.rule == kept.rule },
                "Direct class starts must include actual U although only T is named by the cleaner")
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,t), "F1 default is provisional")
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
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(residual,kept)),false,emptySet()))
            val profile = p.staticProfiles()[callee.method]
            assertTrue(profile.footprint.ownsPoint(cls))
            assertFalse(u in profile.classPointMarks(cls),"Only T is named by the cleaner metadata")
            val (report,seen) = execute(f,root,listOf(1))
            assertEquals(ReportState.DEMAND,report.entries.single { it.key.rule == residual.rule }.state)
            assertTrue(report.entries.any { it.key.rule == kept.rule })
            val summaries = seen.results.single().analyzers.single { it.key == callee }.summaries.all().toList()
            assertTrue(summaries.any { (premise,conclusion) ->
                premise is InitialAp && premise.pathData.toList() == path && premise.tail == Tail.STAR &&
                    premise.mark == MarkSlot.Concrete(t) && conclusion is TaintTree && conclusion.layer == Layer.DEMAND &&
                    f.ops.leaves(conclusion).any { it.fact.path == path && it.fact.tail == Tail.ANY &&
                        it.fact.mark == MarkSlot.Concrete(t) }
            },"The residual comes from the real chain's ordinary PART cleaner, not a raw root identity")
            assertTrue(summaries.any { (premise,_) -> premise is InitialAp &&
                premise.pathData.toList() == path && premise.tail == Tail.STAR && premise.mark == MarkSlot.Concrete(u) },
                "All actual U is admitted without a class request key or a named U metadata need")
            assertTrue(seen.summaries.any { row -> row.part.callee == callee &&
                row.publication.premise is InitialAp && isSpecialStaticRoot(row.publication.premise as InitialAp) },
                "The ordinary default is applied without deriving a root method summary")
            assertTrue(seen.processed.none { row -> row.delta.premise is InitialAp &&
                isSpecialStaticRoot(row.delta.premise as InitialAp) })
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,u))
        }
    }

    @Test fun `F1 nonreturning default is only a candidate and strong execution has no return`() {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("noreturn root",listOf(listOf(1),listOf(2),emptyList()),2).key()
            val callee = p.method("noreturn callee",listOf(listOf(0),emptyList()),1).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val field = p.field("g")
            val path = listOf(cls,field); val t = p.mark("T")
            source(f,root,p.pattern(STATIC,path,mark=MarkSlot.Concrete(t)).fact)
            p.call(root,1,call(f,callee))
            val after = sink(f,"after nonreturn",path,t)
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(after)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3))
            assertFalse(report.entries.any { it.key.rule == after.rule })
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,t), "F1 defaults do not certify return")
            assertFalse(hasTaint(f,seen.processed,3,root,2,path,t))
            assertTrue(seen.results.first().witnessesByKey().any { it.key.rule == after.rule })
            assertTrue(seen.results.filter { it.runIndex > 1 }.all { result ->
                result.analyzers.single { it.key == callee }.summaries.all().none()
            }, "No strong callee answer can be inferred from its missing normal-return path")
        }
    }

    @Test fun `only strong records are reusable and actual queries survive request catalog release`() {
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
            }.build())
            // F1 still cuts the source, but the sink's exact deep requirement
            // gives B2 a NORMAL exact query and therefore a reusable strong record.
            val target = sink(f,"record survivor",gp+deeper,t)
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(target)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3),replayBackward=true)
            assertEquals(listOf(1,2,3,4),seen.invoked)
            assertEquals(3,report.end.run)
            assertTrue(seen.records.none { it.run == 2 })
            assertTrue(seen.views.none { it.run == 2 }, "F1 has no executable native/opposite records")
            assertTrue(seen.results.filter { it.runIndex in 2..3 }.all { result ->
                result.analyzers.single { it.key == callee }.nonZeroInitial
            }, "Actual callee queries are independent of missing F1 static field summaries")
            assertTrue(seen.views.any { it.run == 3 && it.part.callee == callee },
                "F3 can reuse a proven opposite B2 record")
            assertTrue(seen.records.any { it.run == 4 && it.record.method == callee &&
                it.record.direction == Direction.BACKWARD && it.catalogReleased },
                "A fresh B4 engine can read completed B2 native records after catalog release")
            assertTrue(hasTaint(f,seen.processed,2,root,0,gp+deeper,t))
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
            p.entry(root, RuleStatement(f.summary(ZERO_KEEP, gen), StatementSummary.EMPTY, emptyList()))
            p.statement(root, 1, MicroEdgeBuilder().apply { read(x, STATIC, fp) }.build())

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
            p.statement(root, 3, MicroEdgeBuilder().apply {
                write(STATIC, gp, weak = false, values = listOf(y), aliasPaths = emptyList())
            }.build())
            val target = sink(f, "nested mixed target", gp, t)
            p.exit(root, 4, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(target)), false, emptySet()))
            val (report, seen) = execute(f, root, listOf(1, 2, 3))
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
