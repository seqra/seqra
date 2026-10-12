package org.opentaint.dataflow.jvm.bidi

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.runBlocking
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaExpressionToAnonymousClassTransformerFeature
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.TypeConstraintInfo
import org.opentaint.dataflow.jvm.bidi.fixtures.CallRules
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.fixtures.RecordingTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRMethodEntries
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.*
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.impl.cfg.JIRInstListImpl
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

/** Expected ownership scopes and independently named JVM witnesses pin the facade wiring. */
class JIRMethodCacheTest : BidiJIRTestUtils() {
    private val callOwner = "sample.bidi.BidiCallSample"
    private val arg0 = AccessPathBase.Argument(0)
    private val arg1 = AccessPathBase.Argument(1)
    private fun call(method: JIRMethod, name: String): JIRInst =
        method.instList.instructions.first { it.callExpr?.method?.method?.name == name }
    private fun edgeStage(plan: CallPlan, kind: StageKind) =
        plan.stages.filterIsInstance<CallStage.Edges>().filter { it.kind == kind }
    private fun callees(plan: CallPlan): Set<JIRMethod> = plan.stages.filterIsInstance<CallStage.Callees>()
        .flatMap { it.callees }.map { it.method as JIRMethod }.toSet()
    private fun primitiveMap(index: Int, methods: Set<JIRMethod>): Int2ObjectMap<Set<JIRMethod>> =
        Int2ObjectOpenHashMap<Set<JIRMethod>>().apply { put(index, methods) }
    private fun otherKey(method: JIRMethod): MethodKey = key(method,
        JIRArgumentTypeMethodContext(0, TypeConstraintInfo(method.enclosingClass, exactType = false)))

    @Test fun `method and key caches keep canonical entries and distinct context forms`(): Unit {
        val method = method("copy")
        val a = key(method)
        val b = otherKey(method)
        assertSame(entries[method], entries[a])
        assertSame(entries[a], entries[b])
        assertSame(entries[a], entries[a])
        assertSame(interp.forms(a), interp.forms(key(method)))
        assertSame(interp.forms(b), interp.forms(b))
        assertNotSame(interp.forms(a), interp.forms(b))
        assertNotSame(entries[method], entries[method("fieldRead")])
    }

    @Test fun `statements and their reversals are shared by every context of a method`(): Unit {
        val method = method("copy")
        val inst = method.instList.instructions.filterIsInstance<JIRAssignInst>().last()
        val a = interp.forms(key(method))
        val b = interp.forms(otherKey(method))
        val backward = a.statement(Direction.BACKWARD, inst)
        val forward = a.statement(Direction.FORWARD, inst)
        assertSame(forward, b.statement(Direction.FORWARD, inst))
        assertSame(backward, b.statement(Direction.BACKWARD, inst))
        assertSame(forward, interp.statementSummary(key(method), inst))
        assertNotSame(forward, backward)
        assertEquals(forward.reversed().edges.map { it.edge }, backward.edges.map { it.edge })
        for (me in backward.edges) assertTrue(forward.edges.any { it.edge === me.forward })
    }

    @Test fun `exit forms share method scope and retain forward removals only in forward direction`(): Unit {
        val method = method("copy")
        val a = interp.forms(key(method))
        val b = interp.forms(otherKey(method))
        for (exit in listOf(normalExit(method), exceptionalExit(method))) {
            val backward = a.exitRules(Direction.BACKWARD, exit)
            val forward = a.exitRules(Direction.FORWARD, exit)
            assertSame(forward, b.exitRules(Direction.FORWARD, exit))
            assertSame(backward, b.exitRules(Direction.BACKWARD, exit))
            assertSame(forward, interp.exitRules(key(method), exit))
            assertFalse(backward.globalStateDrop)
            assertTrue(backward.entryMarks.isEmpty())
            assertEquals(forward.rules.reversed().summary.edges.map { it.edge }, backward.rules.summary.edges.map { it.edge })
            assertFalse(forward.globalStateDrop, "No exit sink requests a static drop")
        }
        assertNotSame(a.exitRules(Direction.FORWARD, normalExit(method)), a.exitRules(Direction.FORWARD, exceptionalExit(method)))
    }

    @Test fun `entry forms have key scope and cache both directions`(): Unit {
        val method = method("copy")
        val a = interp.forms(key(method))
        val b = interp.forms(otherKey(method))
        val backward = a.entryRules(Direction.BACKWARD)
        val forward = a.entryRules(Direction.FORWARD)
        assertSame(forward, a.entryRules(Direction.FORWARD))
        assertSame(backward, a.entryRules(Direction.BACKWARD))
        assertSame(forward, interp.entryRules(key(method)))
        assertNotSame(forward, b.entryRules(Direction.FORWARD))
        assertNotSame(backward, b.entryRules(Direction.BACKWARD))
        assertEquals(forward.reversed().summary.edges.map { it.edge }, backward.summary.edges.map { it.edge })
    }

    @Test fun `call plans have key scope and cache the forward value before its reversal`(): Unit {
        val method = method("staticCall", callOwner)
        val inst = call(method, "target")
        val expr = checkNotNull(inst.callExpr)
        val rules = CallRules()
        val owner = interp(rules)
        val a = owner.forms(key(method))
        val b = owner.forms(otherKey(method))
        val backward = a.call(Direction.BACKWARD, inst, expr)
        val forward = a.call(Direction.FORWARD, inst, expr)
        assertEquals(CallPoint.AFTER, backward.entry)
        assertEquals(CallPoint.BEFORE, backward.exit)
        assertSame(forward, owner.callPlan(key(method), inst, expr))
        assertSame(backward, a.call(Direction.BACKWARD, inst, expr))
        assertNotSame(forward, backward)
        assertEquals(setOf(method("target", callOwner)), callees(forward))
        assertEquals(listOf("sink" to false, "source" to false, "cleaner" to false,
            "source" to true, "cleaner" to true), rules.queries.map { it.kind to it.allRelevant })
        assertNotSame(forward, b.call(Direction.FORWARD, inst, expr))
        assertNotSame(backward, b.call(Direction.BACKWARD, inst, expr))
        assertEquals(10, rules.queries.size)
        assertTrue(rules.queries.all { it.fact == null })
    }

    @Test fun `real call resolver reads the key context and selects an exact virtual receiver`(): Unit {
        val method = method("virtualCall", callOwner)
        val inst = call(method, "virtual")
        val expr = checkNotNull(inst.callExpr)
        val derived = checkNotNull(cp.findClassOrNull("$callOwner\$Derived"))
        val constrained = key(method, JIRArgumentTypeMethodContext(0, TypeConstraintInfo(derived, exactType = true)))
        val general = interp.callPlan(key(method), inst, expr)
        val exact = interp.callPlan(constrained, inst, expr)
        assertEquals(setOf("$callOwner\$Base", "$callOwner\$Derived"), callees(general).map { it.enclosingClass.name }.toSet())
        assertEquals(setOf("$callOwner\$Derived"), callees(exact).map { it.enclosingClass.name }.toSet())
        assertNotSame(general, exact)
        assertSame(exact, interp.forms(constrained).call(Direction.FORWARD, inst, expr))
    }

    @Test fun `static read sources use the reduced provider once across method contexts`(): Unit {
        val method = method("staticRead")
        val inst = method.instList.instructions.filterIsInstance<JIRAssignInst>().first { it.rhv is JIRFieldRef }
        val field = (inst.rhv as JIRFieldRef).field.field
        val rules = RecordingTaintRules(listOf(TaintStaticFieldSource(field, mkTrue(),
            listOf(AssignMark(RuleMark("READ"), Result)), null)))
        val owner = interp(rules)
        val first = owner.statementSummary(key(method), inst)
        val second = owner.statementSummary(otherKey(method), inst)
        val lhs = assertNotNull(accessPathBase(inst.lhv))
        assertSame(first, second)
        assertEquals(1, rules.staticQueries.size)
        val query = rules.staticQueries.single()
        assertSame(field, query.field)
        assertSame(inst, query.statement)
        assertNull(query.fact)
        assertFalse(query.allRelevant)
        val expected = PathEdge(ZERO_FACT, PathFact(lhs, emptyList(), Tail.EXACT,
            MarkSlot.Concrete(manager.marks.mark("READ"))), ExclusionSet.Empty)
        assertTrue(first.edges.any { it.edge == expected })
        assertTrue(first.edges.any { it.edge == ZERO_KEEP })
    }

    @Test fun `real local alias provider adds an untouched full heap path to a field write`(): Unit {
        val method = method("writeThroughFieldAlias", "sample.alias.HeapAliasSample")
        val inst = method.instList.instructions.filterIsInstance<JIRAssignInst>().single { it.lhv is JIRFieldRef }
        val target = inst.lhv as JIRFieldRef
        val local = assertIs<AccessPathBase.LocalVar>(assertNotNull(accessPathBase(checkNotNull(target.instance))))
        val src = assertNotNull(accessPathBase(inst.rhv as JIRValue))
        val owner = interp(aliasParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val summary = owner.statementSummary(key(method), inst)
        val box = manager.accessors.index(FieldAccessor("sample.alias.HeapAliasSample\$Nested", "box", "sample.alias.HeapAliasSample\$Box"))
        val value = manager.accessors.index(FieldAccessor("sample.alias.HeapAliasSample\$Box", "value", "java.lang.Object"))
        val expected = PathEdge(PathFact(src, emptyList(), Tail.STAR, MarkSlot.STAR),
            PathFact(arg0, listOf(box, value), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
        assertTrue(summary.edges.any { it.edge == expected })
        assertFalse(arg0 in summary.touched)
        assertTrue(local in summary.touched)
        assertFalse(interp.statementSummary(key(method), inst).edges.any { it.edge == expected })
        assertNotNull(owner.entries[method].aliasAnalysis)
        assertSame(owner.entries[method].aliasAnalysis, owner.entries[method].aliasAnalysis)
    }

    @Test fun `call bind back wires the actual persisted alias helper`(): Unit {
        val method = method("aliasOverwritten", callOwner)
        val inst = call(method, "external")
        val owner = interp(aliasParams = JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val plan = owner.callPlan(key(method), inst, checkNotNull(inst.callExpr))
        val f = manager.accessors.index(FieldAccessor(callOwner, "f", "java.lang.Object"))
        assertIs<AccessPathBase.LocalVar>(assertNotNull(accessPathBase(checkNotNull(inst.callExpr).args[0])))
        val aliases = edgeStage(plan, StageKind.ALIASES)
        assertTrue(aliases.isNotEmpty())
        assertTrue(aliases.all { it.guard === Guard.MemoryEffect })
        val expected = PathEdge(PathFact(arg0, emptyList(), Tail.STAR, MarkSlot.STAR),
            PathFact(arg0, listOf(f), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
        assertTrue(aliases.flatMap { it.summary.edges }.any { it.edge == expected })
        assertTrue(edgeStage(interp.callPlan(key(method), inst, checkNotNull(inst.callExpr)), StageKind.ALIASES).isEmpty())
    }

    @Test fun `prescan lambda snapshot owns the outer primitive and set levels before entries are read`(): Unit {
        val caller = method("lambdaCall", callOwner)
        val inst = call(caller, "apply")
        val impl = method("lambdaImpl", callOwner)
        val targets = linkedSetOf(impl)
        val byIndex = primitiveMap(inst.location.index, targets)
        val raw = hashMapOf(caller to byIndex)
        val owner = interp(prescanLambdas = raw)
        targets.clear()
        byIndex.clear()
        raw.clear()
        val frozen = owner.entries.prescanLambdas
        assertEquals(setOf(caller), frozen.keys)
        assertEquals(setOf(inst.location.index), frozen.getValue(caller).keys)
        assertEquals(setOf(impl), frozen.getValue(caller)[inst.location.index])
        assertNotSame(raw, frozen)
        assertNotSame(byIndex, frozen.getValue(caller))
        assertNotSame(targets, frozen.getValue(caller)[inst.location.index])
        assertFailsWith<UnsupportedOperationException> { (frozen as MutableMap<*, *>).clear() }
        assertFailsWith<UnsupportedOperationException> { frozen.getValue(caller).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozen.getValue(caller)[inst.location.index] as MutableSet<*>).clear() }
        assertSame(frozen.getValue(caller), owner.entries[caller].lambdas)
        assertEquals(setOf(impl), callees(owner.callPlan(key(caller), inst, checkNotNull(inst.callExpr))))
    }

    @Test fun `missing caller lambda snapshot is immutable and cannot modify another method`(): Unit {
        val first = entries[method("copy")].lambdas
        val second = entries[method("fieldRead")].lambdas
        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertFailsWith<UnsupportedOperationException> { first.put(1, setOf(method("copy"))) }
        assertTrue(second.isEmpty())
    }

    @Test fun `prescan primitive entries cannot replace frozen lambda target sets`(): Unit {
        val caller = method("lambdaCall", callOwner)
        val inst = call(caller, "apply")
        val impl = method("lambdaImpl", callOwner)
        val owner = interp(prescanLambdas = mapOf(caller to primitiveMap(inst.location.index, setOf(impl))))
        val frozen = owner.entries.prescanLambdas.getValue(caller)
        assertFailsWith<UnsupportedOperationException> {
            frozen.int2ObjectEntrySet().single().setValue(setOf(method("lambdaOther", callOwner)))
        }
        assertEquals(setOf(impl), frozen[inst.location.index])
        assertEquals(setOf(impl), callees(owner.callPlan(key(caller), inst, checkNotNull(inst.callExpr))))
    }

    @Test fun `prescan generic entries cannot replace frozen lambda target sets`(): Unit {
        val caller = method("lambdaCall", callOwner)
        val inst = call(caller, "apply")
        val impl = method("lambdaImpl", callOwner)
        val owner = interp(prescanLambdas = mapOf(caller to primitiveMap(inst.location.index, setOf(impl))))
        val frozen: MutableMap<Int, Set<JIRMethod>> = owner.entries.prescanLambdas.getValue(caller)
        assertFailsWith<UnsupportedOperationException> {
            frozen.entries.single().setValue(setOf(method("lambdaOther", callOwner)))
        }
        assertEquals(setOf(impl), frozen[inst.location.index])
        assertEquals(setOf(impl), callees(owner.callPlan(key(caller), inst, checkNotNull(inst.callExpr))))
    }

    @Test fun `canonical method entries and key forms publish once under concurrent lookup`(): Unit {
        val method = method("copy")
        val key = key(method)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll((0 until 32).map { Callable { entries[method] to interp.forms(key) } }).map { it.get() }
            for ((entry, forms) in results) {
                assertSame(results.first().first, entry)
                assertSame(results.first().second, forms)
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun `graph values are cached independently by direction with boundary exit metadata`(): Unit {
        val method = method("copy")
        val entry = entries[method]
        val forward = entry.graph(Direction.FORWARD)
        val backward = entry.graph(Direction.BACKWARD)
        assertSame(forward, entry.graph(Direction.FORWARD))
        assertSame(backward, entry.graph(Direction.BACKWARD))
        assertNotSame(forward, backward)
        assertTrue(forward.isExitPoint(languageManager, normalExit(method)))
        assertTrue(forward.isExitPoint(languageManager, exceptionalExit(method)))
        assertTrue(backward.isExitPoint(languageManager, key(method).statement))
        assertEquals(method.instList.size, forward.instructions.size)
        assertEquals(method.instList.instructions.toList(), backward.instructions.toList())
        for (inst in method.instList.instructions) assertEquals(inst.location.index, entry.index(inst))
    }

    @Test fun `facade exposes both typed boundary exits and exactly the method key entry`(): Unit {
        val method = method("throwValue")
        val key = otherKey(method)
        assertSame(key.statement, interp.entryNode(key))
        val exits = interp.exitNodes(key)
        assertEquals(listOf(normalExit(method), exceptionalExit(method)), exits.map { it.node })
        assertEquals(listOf(false, true), exits.map { it.exceptional })
        val forms = DirectedForms(interp, Direction.BACKWARD, interp::forms)
        assertEquals(listOf(normalExit(method)), forms.startNodes(key, zero = false))
        assertEquals(listOf(normalExit(method), exceptionalExit(method)), forms.startNodes(key, zero = true))
        assertEquals(listOf(key.statement), forms.endNodes(key))
    }

    @Test fun `summary base and accessor boundaries include zero but exclude locals and temporary bases`(): Unit {
        val supported = listOf(AccessPathBase.Zero, AccessPathBase.This, arg0, arg1,
            AccessPathBase.Return, AccessPathBase.Exception, AccessPathBase.ClassStatic, AccessPathBase.Constant("java.lang.String", "x"))
        assertTrue(supported.all(interp::isSummaryBase))
        assertFalse(interp.isSummaryBase(AccessPathBase.LocalVar(7)))
        assertFalse(interp.isSummaryBase(AccessPathBase.CleanerTemporary()))
        val field = FieldAccessor("Owner", "f", "java.lang.Object")
        val cls = ClassStaticAccessor("Owner")
        for (a in listOf(field, cls, ElementAccessor)) assertEquals(manager.accessors.index(a), interp.idx(a))
        assertFails { interp.idx(TaintMarkAccessor("T")) }
    }

    @Test fun `builder caching preserves source predicate descriptors and reverses branches on demand`(): Unit {
        val caller = method("staticCall", callOwner)
        val inst = call(caller, "target")
        val target = method("target", callOwner)
        val condition: Condition = CommonCondition.And(listOf(
            CommonCondition.Atom<JirCondition>(ContainsMark(Argument(0), RuleMark("T"))),
            CommonCondition.Atom<JirCondition>(ContainsMark(Argument(1), RuleMark("U")))))
        val rules = CallRules().apply {
            sources = listOf(TaintMethodSource(target, condition, listOf(AssignMark(RuleMark("OUT"), Result)), null))
        }
        val owner = interp(rules)
        val forms = owner.forms(key(caller))
        val plan = forms.call(Direction.FORWARD, inst, checkNotNull(inst.callExpr))
        val source = edgeStage(plan, StageKind.SOURCES).single().summary.sources.single()
        assertEquals(2, source.predicate.leaves.size)
        assertTrue(source.predicate.leaves.indices.all { source.predicate.isMulti(it) && !source.predicate.isUnary(it) })
        val backward = forms.call(Direction.BACKWARD, inst, checkNotNull(inst.callExpr))
        val reversed = edgeStage(backward, StageKind.SOURCES).single().summary.sources.single()
        assertSame(source.predicate, reversed.predicate)
        assertSame(source.actions, reversed.actions)
        assertEquals(source.id, reversed.id)
        assertTrue(reversed.backward)
        repeat(20) {
            assertSame(plan, forms.call(Direction.FORWARD, inst, checkNotNull(inst.callExpr)))
            assertSame(backward, forms.call(Direction.BACKWARD, inst, checkNotNull(inst.callExpr)))
            assertSame(source, edgeStage(plan, StageKind.SOURCES).single().summary.sources.single())
        }
        assertTrue(edgeStage(backward, StageKind.SOURCES).single().summary.edges.none { it.conjunctive })
        for (literal in source.predicate.leaves) {
            val forward = PathEdge(literal.fact, source.actions.single().target, ExclusionSet.Empty)
            val transfer = reversed.transfer(literal, reversed.actions.single(), multi = true)
            assertEquals(forward, transfer.forward)
            assertEquals(revEdge(forward), transfer.edge)
            assertTrue(transfer.conjunctive && transfer.may)
        }
    }

    @Test fun `unresolved tracking is triggered by arrivals and uses pass-read metadata`(): Unit {
        val tracker = ExternalMethodTracker()
        val caller = method("staticExternal", callOwner)
        val inst = call(caller, "external")
        val expr = checkNotNull(inst.callExpr)
        val owner = interp(externalMethodTracker = tracker)
        val plan = owner.callPlan(key(caller), inst, expr)
        assertTrue(tracker.getExternalMethods().withoutRules.isEmpty())
        assertTrue(tracker.getExternalMethods().withRules.isEmpty())
        owner.reached(expr, arg0, plan)
        owner.reached(expr, arg1, plan)
        owner.reached(expr, AccessPathBase.ClassStatic, plan)
        val record = tracker.getExternalMethods().withoutRules.single()
        assertEquals("$callOwner#external", record.method)
        assertEquals(expr.method.method.description, record.signature)
        assertEquals(setOf(arg0.toString(), arg1.toString()), record.factPositions.toSet())
        assertEquals(2, record.callSites)
        val withPass = CallPlan(plan.touched, plan.stages, plan.sinks, plan.entry, plan.exit, passReads = setOf(arg0))
        owner.reached(expr, arg0, withPass)
        assertTrue(tracker.getExternalMethods().withoutRules.isEmpty())
        assertEquals(3, tracker.getExternalMethods().withRules.single().callSites)
        interp.reached(expr, arg0, plan)
    }

    @Test fun `native empty methods have no analysable entry`(): Unit {
        val empty = method("emptyLambda", callOwner)
        assertEquals(0, empty.instList.size)
        assertFailsWith<IllegalStateException> { entries[empty] }
    }

    @Test fun `missing either linked lambda feature is rejected before method analysis`(): Unit = runBlocking {
        val lambda = LambdaAnonymousClassFeature()
        val first = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses, JMethodBoundaryInstFeature))
        val second = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses, lambda, JMethodBoundaryInstFeature))
        val third = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses,
            LambdaExpressionToAnonymousClassTransformerFeature(lambda), JMethodBoundaryInstFeature))
        for (bad in listOf(first, second, third)) try {
            val badGraph = JApplicationSingleExitGraph(JApplicationGraphImpl(bad, bad.usagesExt()))
            assertFailsWith<IllegalStateException> {
                JIRMethodEntries(badGraph, JIRLanguageManager(bad), callResolver, NoTaintRules,
                    JIRLocalAliasAnalysis.Params(useAliasAnalysis = false), manager.cancellation, emptyMap())
            }
        } finally { bad.close() }
    }

    @Test fun `a last exceptional instruction does not admit an incomplete or reordered boundary triple`(): Unit = runBlocking {
        for (mode in 0..2) {
            val lambda = LambdaAnonymousClassFeature()
            val corrupt = object : JIRInstExtFeature {
                override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
                    if (list.size == 0) return list
                    val instructions = when (mode) {
                        0 -> list.instructions.filterNot { it is JMethodEnterInst }
                        1 -> list.instructions.filterNot { it is JMethodExitNormalInst }
                        else -> list.instructions.toMutableList().apply {
                            val n = size
                            val enter = this[n - 3]
                            this[n - 3] = this[n - 2]
                            this[n - 2] = enter
                        }
                    }
                    return JIRInstListImpl(instructions)
                }
            }
            val bad = db.classpath(listOf(samplesJar.toFile()), listOf(UnknownClasses, lambda,
                LambdaExpressionToAnonymousClassTransformerFeature(lambda), JMethodBoundaryInstFeature, corrupt))
            try {
                val badGraph = JApplicationSingleExitGraph(JApplicationGraphImpl(bad, bad.usagesExt()))
                val badEntries = JIRMethodEntries(badGraph, JIRLanguageManager(bad), callResolver, NoTaintRules,
                    JIRLocalAliasAnalysis.Params(useAliasAnalysis = false), manager.cancellation, emptyMap())
                val method = checkNotNull(bad.findClassOrNull("sample.bidi.BidiStatementSample")).declaredMethods.single { it.name == "copy" }
                assertIs<JMethodExitExceptionalInst>(method.instList.instructions.last())
                assertFailsWith<IllegalStateException> { badEntries[method] }
            } finally { bad.close() }
        }
    }
}
