package org.opentaint.dataflow.bidi

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
import kotlin.time.Duration.Companion.seconds

/** Exercise the actual driver, caller subscriptions, return continuations and native reader. */
class Run1StaticDriverSafetyTest {
    private data class Processed(val run: Int, val method: MethodKey, val delta: EdgeDelta)
    private data class ReadRecord(val run: Int, val part: Subscription, val record: Record,
                                  val catalogReleased: Boolean, val footprintPresent: Boolean)
    private data class ReadSummary(val run: Int, val part: Subscription, val publication: Publication)
    private class Observation {
        val results = arrayListOf<RunResult<RunMethodAnalyzer>>()
        val processed = ConcurrentLinkedQueue<Processed>()
        val records = ConcurrentLinkedQueue<ReadRecord>()
        val summaries = ConcurrentLinkedQueue<ReadSummary>()
        val invoked = arrayListOf<Int>()
    }

    private fun execute(f: DriverFixtures, root: MethodKey, limits: List<Int>): Pair<Report, Observation> {
        val seen = Observation()
        f.program.freshShared().use { owner ->
            val driver = IterationDriver(FixedLimits(limits), owner, 20.seconds,
                invokeRun = { config, shared, timeout ->
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
                    RunManager(config, shared, factory, quantum = 1).run(timeout).also { seen.results += it }
                }, guardBarrier = { _, body -> body() })
            val report = driver.analyze(listOf(root))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertTrue(seen.results.all { it.status == RunStatus.COMPLETE })
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

    @Test fun `actual driver field cleaner removes T without a root return restoring it and preserves U`() {
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
            val (report,seen) = execute(f,root,listOf(1))
            assertFalse(report.entries.any { it.key.rule == removed.rule })
            assertTrue(report.entries.any { it.key.rule == kept.rule })
            assertFalse(hasTaint(f,seen.processed,1,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,u))
            val rootControl = seen.results.single().analyzers.single { it.key == callee }.summaries.all()
                .filter { (premise,_) -> premise is InitialAp && isSpecialStaticRoot(premise) }.toList()
            assertTrue(rootControl.any { hasStaticRootReturnEvidence(it.second) },
                "A reached root control summary exists, but cannot supply the cleaned owned field")
        }
    }

    @Test fun `actual driver strong static write removes f and frames untouched g despite coarse class E`() {
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
            val (report,seen) = execute(f,root,listOf(1))
            assertFalse(report.entries.any { it.key.rule == killed.rule })
            assertTrue(report.entries.any { it.key.rule == preserved.rule })
            assertFalse(hasTaint(f,seen.processed,1,root,2,fp,t))
            assertTrue(hasTaint(f,seen.processed,1,root,2,gp,t))
            assertTrue(seen.results.single().analyzers.single { it.key == callee }.summaries.all().any { (premise,facts) ->
                premise is InitialAp && isSpecialStaticRoot(premise) && facts is FlowTree &&
                    hasStaticRootReturnEvidence(facts) && !facts.exclusion.admits(cls)
            },"The actual raw root summary's coarse E must not erase g")
        }
    }

    @Test fun `actual driver owned exact class cleaner removes T at the point and preserves U`() {
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
            val (report,seen) = execute(f,root,listOf(1))
            assertFalse(report.entries.any { it.key.rule == removed.rule },
                "The owned class point cannot regain T through its chain summary or the root frame")
            assertTrue(report.entries.any { it.key.rule == kept.rule },
                "Direct class starts must include actual U although only T is named by the cleaner")
            assertFalse(hasTaint(f,seen.processed,1,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,u))
        }
    }

    @Test fun `broad class chain keeps ordinary PART demand residual while owned point gets no root frame`() {
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
            assertFalse(seen.summaries.any { row -> row.part.callee == callee &&
                row.publication.premise is InitialAp && isSpecialStaticRoot(row.publication.premise as InitialAp) },
                "The reader excludes the owned point before any root frame application")
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,t))
            assertTrue(hasTaint(f,seen.processed,1,root,2,path,u))
        }
    }

    @Test fun `actual driver nonreturning callee cannot authorize an untouched static frame`() {
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
            val (report,seen) = execute(f,root,listOf(1))
            assertFalse(report.entries.any { it.key.rule == after.rule })
            assertFalse(hasTaint(f,seen.processed,1,root,2,path,t))
            assertTrue(seen.results.single().analyzers.single { it.key == callee }.summaries.all().none(),
                "Reaching the method input is not normal-return evidence")
        }
    }

    @Test fun `actual driver reads opposite and native root records after request catalog release`() {
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
            val target = sink(f,"record survivor",gp,t,Tail.ANY)
            p.exit(root,2,ExitRules(RuleStatement(StatementSummary.EMPTY,StatementSummary.EMPTY,
                listOf(target)),false,emptySet()))
            val (report,seen) = execute(f,root,listOf(1,2,3))
            assertEquals(listOf(1,2,3),seen.invoked)
            assertEquals(3,report.end.run)
            for (result in seen.results.filter { it.runIndex > 1 }) {
                assertFalse(result.analyzers.single { it.key == callee }.nonZeroInitial,
                    "The restricted callee has no nonzero demand; its passive record channel must suffice")
            }
            val rootReads = seen.records.filter { it.record.method == callee && isSpecialStaticRoot(it.record.premise) }
            for (run in listOf(2,3)) {
                val reads = rootReads.filter { it.run == run && it.record.direction == Direction.FORWARD }
                assertTrue(reads.isNotEmpty(),"Run $run must really enter the persisted F1 root record channel")
                assertTrue(reads.all { it.catalogReleased && it.footprintPresent &&
                    hasStaticRootReturnEvidence(it.record.conclusion) })
                assertTrue(reads.any { read -> f.ops.leaves(read.part.added).any {
                    it.fact.path.take(2) == gp && it.fact.mark == MarkSlot.Concrete(t)
                } },"Root record reads keep the original g supplier, not an unmasked root identity")
            }
            assertTrue(hasTaint(f,seen.processed,2,root,0,gp,t),
                "The opposite record must resume the actual backward caller continuation")
            assertTrue(hasTaint(f,seen.processed,3,root,2,gp+deeper,t),
                "The native record must resume the actual forward caller continuation")
            assertTrue(seen.processed.filter { it.run > 1 }.all { row ->
                val facts = row.delta.facts
                facts !is FlowTree || facts.base != STATIC ||
                    (facts.root.leaf.isEmpty && facts.root.childData.orEmpty().all { it.leaf.isEmpty })
            },"Actual handoff/record routing must not seed restricted broad FLOW in this program")
            assertTrue(report.entries.any { it.key.rule == target.rule })
        }
    }

    @Test fun `restricted strong keep disproves a primitive-only NORMAL broad FLOW preservation argument`() {
        DriverFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C")); val field = f.program.field("f")
            val initial = f.manager.initial(STATIC,null,Tail.STAR,ExclusionSet.Empty,MarkSlot.STAR)
            val keep = keepEdge(STATIC,listOf(cls),ExclusionSet.of(field))
            val selected = CollectingOut()
            f.ops.applyEdge(f.ops.startFact(initial),initial,keep,true,
                ApMode(true,Direction.FORWARD,1),selected)
            assertTrue(selected.results.isEmpty(),"The selected run-1 parent branch is suppressed")
            val restricted = CollectingOut()
            f.ops.applyEdge(f.ops.startFact(initial),initial,keep,true,
                ApMode(false,Direction.BACKWARD,2),restricted)
            val result = assertIs<FlowTree>(restricted.results.single())
            assertEquals(Layer.DEMAND,result.layer)
            assertFalse(result.root.child(cls)!!.leaf.isEmpty)
            // Thus later-reader safety needs the actual HandOff/no-broad-entry
            // invariant, not a claim that every generic primitive preserves it.
        }
    }
}
